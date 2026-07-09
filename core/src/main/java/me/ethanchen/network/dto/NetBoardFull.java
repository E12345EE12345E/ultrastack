package me.ethanchen.network.dto;

/** Bigger board snapshot, sent initially and then rarely on desyncs (avoiding excessive bandwidth usage). */
public class NetBoardFull {
    public byte[] tileid;
    public byte[] tileconnections;
    public boolean[] allowedtiles;
    public byte width;
    public byte height;
    public byte[] spawnposx;
    public byte[] spawnposy;
    public NetQueue[] queues;
    public NetPiece[] pieces;
}
