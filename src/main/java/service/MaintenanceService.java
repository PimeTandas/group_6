package service;

import domains.MaintenanceTeam;
import domains.Transporter;

public interface MaintenanceService {

    // gives an AVAILABLE team to a DOCKED transport, the transport becomes SERVICING
    void assignTeam(Transporter transport, MaintenanceTeam team);

    // transport becomes READY and its team is free again
    void completeMaintenance(Transporter transport);
}
