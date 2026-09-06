/*
 * Optional C implementation of the Windows launcher.
 * The shipped start.exe is built from win-launcher/ via build-start.bat.
 */
#define UNICODE
#define _UNICODE
#define WIN32_LEAN_AND_MEAN

#include <windows.h>
#include <shellapi.h>

#include <jni.h>

#include <stdio.h>
#include <string.h>
#include <wchar.h>

#ifdef __GNUC__
#define EXPORT __attribute__((dllexport))
#else
#define EXPORT __declspec(dllexport)
#endif

/* Drivers read these from the process that creates the OpenGL context. */
EXPORT DWORD NvOptimusEnablement = 0x00000001;
EXPORT int AmdPowerXpressRequestHighPerformance = 1;

#define MAIN_CLASS "me/ethanchen/lwjgl3/Lwjgl3Launcher"
#define JAR_GLOB L"UltraStack-*.jar"
#define TITLE L"UltraStack"

typedef jint (JNICALL *CreateJavaVM_t)(JavaVM **, void **, void *);

static void show_error(const wchar_t *message) {
    MessageBoxW(NULL, message, TITLE, MB_OK | MB_ICONERROR);
}

static void show_error_utf8(const char *message) {
    wchar_t wide[1024];
    int n = MultiByteToWideChar(CP_UTF8, 0, message, -1, wide, 1024);
    if (n <= 0) {
        show_error(L"UltraStack failed to start.");
        return;
    }
    show_error(wide);
}

static int to_utf8(const wchar_t *wide, char *out, int out_bytes) {
    int n = WideCharToMultiByte(CP_UTF8, 0, wide, -1, out, out_bytes, NULL, NULL);
    return n > 0 ? 0 : -1;
}

static int exe_directory(wchar_t *out, DWORD out_chars) {
    DWORD n = GetModuleFileNameW(NULL, out, out_chars);
    if (n == 0 || n >= out_chars) {
        return -1;
    }
    wchar_t *slash = wcsrchr(out, L'\\');
    if (slash == NULL) {
        return -1;
    }
    *slash = L'\0';
    return 0;
}

static int find_game_jar(wchar_t *out, DWORD out_chars) {
    WIN32_FIND_DATAW data;
    HANDLE find = FindFirstFileW(JAR_GLOB, &data);
    int count = 0;

    if (find == INVALID_HANDLE_VALUE) {
        show_error(L"No UltraStack-*.jar found. Run update.bat first.");
        return -1;
    }
    do {
        if (data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
            continue;
        }
        count++;
        if (count == 1) {
            if (wcslen(data.cFileName) + 1 > out_chars) {
                FindClose(find);
                show_error(L"JAR path is too long.");
                return -1;
            }
            wcscpy(out, data.cFileName);
        }
    } while (FindNextFileW(find, &data));
    FindClose(find);

    if (count == 0) {
        show_error(L"No UltraStack-*.jar found. Run update.bat first.");
        return -1;
    }
    if (count > 1) {
        show_error(L"Multiple UltraStack-*.jar files found. Leave only one, or run update.bat.");
        return -1;
    }
    return 0;
}

static void prepend_path(const wchar_t *bin_dir) {
    wchar_t old_path[32768];
    wchar_t new_path[32768];
    DWORD n = GetEnvironmentVariableW(L"PATH", old_path, 32768);
    if (n == 0 || n >= 32768) {
        SetEnvironmentVariableW(L"PATH", bin_dir);
        return;
    }
    if (_snwprintf(new_path, 32768, L"%s;%s", bin_dir, old_path) < 0) {
        return;
    }
    SetEnvironmentVariableW(L"PATH", new_path);
}

static void report_java_exception(JNIEnv *env) {
    jthrowable thrown = (*env)->ExceptionOccurred(env);
    if (thrown == NULL) {
        return;
    }
    (*env)->ExceptionClear(env);

    jclass throwable = (*env)->FindClass(env, "java/lang/Throwable");
    if (throwable == NULL) {
        (*env)->ExceptionClear(env);
        show_error(L"UltraStack failed to start (Java exception).");
        return;
    }
    jmethodID to_string = (*env)->GetMethodID(env, throwable, "toString", "()Ljava/lang/String;");
    if (to_string == NULL) {
        (*env)->ExceptionClear(env);
        show_error(L"UltraStack failed to start (Java exception).");
        return;
    }
    jstring message = (jstring) (*env)->CallObjectMethod(env, thrown, to_string);
    if (message == NULL || (*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        show_error(L"UltraStack failed to start (Java exception).");
        return;
    }
    const char *utf = (*env)->GetStringUTFChars(env, message, NULL);
    if (utf != NULL) {
        show_error_utf8(utf);
        (*env)->ReleaseStringUTFChars(env, message, utf);
    } else {
        show_error(L"UltraStack failed to start (Java exception).");
    }
}

static int invoke_main(JNIEnv *env, int argc, LPWSTR *argv) {
    jclass cls = (*env)->FindClass(env, MAIN_CLASS);
    if (cls == NULL) {
        report_java_exception(env);
        if (!(*env)->ExceptionCheck(env)) {
            show_error(L"Could not find me.ethanchen.lwjgl3.Lwjgl3Launcher.");
        }
        return -1;
    }

    jmethodID main = (*env)->GetStaticMethodID(env, cls, "main", "([Ljava/lang/String;)V");
    if (main == NULL) {
        report_java_exception(env);
        show_error(L"Could not find Lwjgl3Launcher.main.");
        return -1;
    }

    jclass string_class = (*env)->FindClass(env, "java/lang/String");
    if (string_class == NULL) {
        report_java_exception(env);
        return -1;
    }

    jint extra = argc > 1 ? (jint) (argc - 1) : 0;
    jobjectArray args = (*env)->NewObjectArray(env, extra, string_class, NULL);
    if (args == NULL) {
        report_java_exception(env);
        return -1;
    }
    for (jint i = 0; i < extra; i++) {
        jstring s = (*env)->NewString(env, (const jchar *) argv[i + 1], (jsize) wcslen(argv[i + 1]));
        if (s == NULL) {
            report_java_exception(env);
            return -1;
        }
        (*env)->SetObjectArrayElement(env, args, i, s);
    }

    (*env)->CallStaticVoidMethod(env, cls, main, args);
    if ((*env)->ExceptionCheck(env)) {
        report_java_exception(env);
        return -1;
    }
    return 0;
}

static const wchar_t *jvm_error_text(jint code) {
    switch (code) {
        case JNI_EVERSION:
            return L"This JDK does not support the required JNI version.";
        case JNI_ENOMEM:
            return L"Not enough memory to start the Java VM.";
        case JNI_EEXIST:
            return L"A Java VM is already running in this process.";
        case JNI_EINVAL:
            return L"Invalid Java VM arguments.";
        default:
            return L"Could not create the Java VM.";
    }
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE prev, PWSTR cmd_line, int show) {
    (void) instance;
    (void) prev;
    (void) cmd_line;
    (void) show;

    wchar_t root[32768];
    wchar_t jar_name[MAX_PATH];
    wchar_t bin_dir[32768];
    wchar_t jvm_path[32768];
    char jar_utf8[32768];
    char classpath_opt[32768 + 32];

    if (exe_directory(root, 32768) != 0 || !SetCurrentDirectoryW(root)) {
        show_error(L"Could not locate the UltraStack folder.");
        return 1;
    }
    if (find_game_jar(jar_name, MAX_PATH) != 0) {
        return 1;
    }
    if (to_utf8(jar_name, jar_utf8, (int) sizeof(jar_utf8)) != 0) {
        show_error(L"JAR path could not be converted to UTF-8.");
        return 1;
    }
    if (_snprintf(classpath_opt, sizeof(classpath_opt), "-Djava.class.path=%s", jar_utf8) < 0) {
        show_error(L"JAR path is too long.");
        return 1;
    }

    if (_snwprintf(bin_dir, 32768, L"%s\\jdk\\bin", root) < 0
            || _snwprintf(jvm_path, 32768, L"%s\\server\\jvm.dll", bin_dir) < 0) {
        show_error(L"JDK path is too long.");
        return 1;
    }
    if (GetFileAttributesW(jvm_path) == INVALID_FILE_ATTRIBUTES) {
        show_error(L"Could not find jdk\\bin\\server\\jvm.dll. Re-extract the Windows zip.");
        return 1;
    }

    prepend_path(bin_dir);
    SetDllDirectoryW(bin_dir);

    HMODULE jvm_dll = LoadLibraryW(jvm_path);
    if (jvm_dll == NULL) {
        show_error(L"Could not load jdk\\bin\\server\\jvm.dll.");
        return 1;
    }
    CreateJavaVM_t create_vm = (CreateJavaVM_t) GetProcAddress(jvm_dll, "JNI_CreateJavaVM");
    if (create_vm == NULL) {
        show_error(L"jdk\\bin\\server\\jvm.dll is missing JNI_CreateJavaVM.");
        return 1;
    }

    JavaVMOption options[3];
    memset(options, 0, sizeof(options));
    options[0].optionString = classpath_opt;
    options[1].optionString = "--enable-native-access=ALL-UNNAMED";
    options[2].optionString = "-Dfile.encoding=UTF-8";

    JavaVMInitArgs args;
    memset(&args, 0, sizeof(args));
    args.version = JNI_VERSION_1_8;
    args.nOptions = 3;
    args.options = options;
    args.ignoreUnrecognized = JNI_TRUE;

    JavaVM *vm = NULL;
    JNIEnv *env = NULL;
    jint rc = create_vm(&vm, (void **) &env, &args);
    if (rc != JNI_OK || vm == NULL || env == NULL) {
        show_error(jvm_error_text(rc));
        return 1;
    }

    int argc = 0;
    LPWSTR *argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    int status = 0;
    if (argv == NULL) {
        show_error(L"Could not read command-line arguments.");
        status = 1;
    } else {
        status = invoke_main(env, argc, argv) == 0 ? 0 : 1;
        LocalFree(argv);
    }

    (*vm)->DestroyJavaVM(vm);
    return status;
}
