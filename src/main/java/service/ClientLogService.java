package service;

import enums.ClientType;

public interface ClientLogService {
    void logConnected(String filename, ClientType clientType);
    void logDisconnected(String filename, ClientType clientType);
}
