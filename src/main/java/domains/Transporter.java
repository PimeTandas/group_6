package domains;

import enums.TransportState;

import java.time.LocalDateTime;

public class Transporter {
    String transporterId;
    TransportState state;
    DockingBay dockingBay;
    int maxCapacity;
    int currentQuantity;
    LocalDateTime departureTime;
    MaintenanceTeam maintenanceTeam;

    Transporter(){}

}
