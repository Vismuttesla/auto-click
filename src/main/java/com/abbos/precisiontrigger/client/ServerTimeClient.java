package com.abbos.precisiontrigger.client;

public interface ServerTimeClient {
    ServerTimeClientResult fetchServerTime(long requestSequence, long configurationVersion);
}