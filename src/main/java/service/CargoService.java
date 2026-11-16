package service;

import domains.Transporter;

public interface CargoService {

    void loadCargo(Transporter transport, int amount);

    void unloadCargo(Transporter transport, int amount);

    void transferCargo(Transporter source,
                       Transporter destination,
                       int amount);
}