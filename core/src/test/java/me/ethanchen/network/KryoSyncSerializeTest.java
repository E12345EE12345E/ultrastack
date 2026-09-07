package me.ethanchen.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import me.ethanchen.network.dto.NetBoardLight;

/**
 * Documents the packet-reuse assumption: Kryo copies {@code byte[]} contents into the output
 * during {@code writeObject}, so mutating the live array afterwards cannot change already
 * serialized bytes. KryoNet {@code sendUDP} serializes on the calling thread before return.
 */
class KryoSyncSerializeTest {

    @Test
    void writeObjectCopiesByteArraysBeforeReturn() {
        Kryo kryo = new Kryo();
        NetworkRegister.registerClasses(kryo);

        NetBoardLight light = new NetBoardLight();
        light.tileid = new byte[]{1, 2, 3, 4};
        light.tileconnections = new byte[]{9, 8, 7, 6};

        Output output = new Output(256);
        kryo.writeObject(output, light);
        byte[] serialized = output.toBytes();

        light.tileid[0] = 99;
        light.tileconnections[0] = 77;

        NetBoardLight copy = kryo.readObject(new Input(serialized), NetBoardLight.class);
        assertEquals(1, copy.tileid[0]);
        assertEquals(9, copy.tileconnections[0]);
        assertNotEquals(99, copy.tileid[0]);
    }
}
