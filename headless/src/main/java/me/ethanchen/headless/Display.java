package me.ethanchen.headless;

public class Display {
    public static void printBoard() {
        
    }

    public static String returnPieceColorString(int id) { // for printing board to console
        switch (id) {
            case 1: return "\u001B[36m";
            case 2: return "\u001B[34m";
            case 3: return "\u001B[33m";
            case 4: return "\u001B[33m";
            case 5: return "\u001B[32m";
            case 6: return "\u001B[35m";
            case 7: return "\u001B[31m";
        }
        return "\u001B[37m";
    }
}
