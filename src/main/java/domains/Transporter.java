package domains;

import enums.TransportState;

import java.time.LocalDateTime;

public class Transporter {

    private final String transporterId;
    private TransportState state;
    private DockingBay dockingBay;
    private int maxCapacity;
    private int currentQuantity;
    private LocalDateTime departureTime;
    private MaintenanceTeam maintenanceTeam;

    public Transporter(String transporterId,
                       TransportState state,
                       DockingBay dockingBay,
                       int maxCapacity,
                       int currentQuantity,
                       LocalDateTime departureTime,
                       MaintenanceTeam maintenanceTeam) {
        this.transporterId = transporterId;
        this.state = state;
        this.dockingBay = dockingBay;
        this.maxCapacity = maxCapacity;
        this.currentQuantity = currentQuantity;
        this.departureTime = departureTime;
        this.maintenanceTeam = maintenanceTeam;
    }

    public String getTransporterId() {
        return transporterId;
    }

    public TransportState getState() {
        return state;
    }

    public void setState(TransportState next) {
        if(this.state.validTransitionTo(next)){
            this.state = next;
        }else{
            throw new IllegalStateException("Invalid state transition, transporter cannot transition from " + this.state + " to " + next);
        }
    }

    public DockingBay getDockingBay() {
        return dockingBay;
    }

    public void setDockingBay(DockingBay dockingBay) {
        this.dockingBay = dockingBay;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public int getCurrentQuantity() {
        return currentQuantity;
    }

    public void setCurrentQuantity(int currentQuantity) {
        this.currentQuantity = currentQuantity;
    }

    public LocalDateTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalDateTime departureTime) {
        this.departureTime = departureTime;
    }

    public MaintenanceTeam getMaintenanceTeam() {
        return maintenanceTeam;
    }

    public void setMaintenanceTeam(MaintenanceTeam maintenanceTeam) {
        this.maintenanceTeam = maintenanceTeam;
    }
}
