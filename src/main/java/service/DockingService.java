package service;

import domains.DockingBay;
import domains.Transporter;

import java.time.LocalDateTime;

public interface DockingService {

    // puts a WAITING transport into an AVAILABLE bay, the transport becomes DOCKED
    void allocateBay(Transporter transport, DockingBay bay);

    // swaps the bays of two transports, both must be DOCKED or READY
    void exchangeBays(Transporter first, Transporter second);

    // admin: closes a bay that has nobody in it
    void closeBay(DockingBay bay);

    // admin: opens a closed bay again
    void reopenBay(DockingBay bay);

    // departs a READY transport if its departure time has passed and frees its bay
    // returns true if the transport departed
    boolean departTransport(Transporter transport, LocalDateTime now);
}
