package domains;

import enums.MaintenanceStatus;

public class MaintenanceTeam {

    private final String maintenanceId;
    private MaintenanceStatus maintenanceStatus;
    private Transporter transporter;

    public MaintenanceTeam(String maintenanceId, MaintenanceStatus maintenanceStatus, Transporter transporter) {
        this.maintenanceId = maintenanceId;
        this.maintenanceStatus = maintenanceStatus;
        this.transporter = transporter;
    }

    public String getMaintenanceId() {
        return maintenanceId;
    }

    public MaintenanceStatus getMaintenanceStatus() {
        return maintenanceStatus;
    }

    public void setMaintenanceStatus(MaintenanceStatus maintenanceStatus) {
        this.maintenanceStatus = maintenanceStatus;
    }

    public Transporter getTransporter() {
        return transporter;
    }

    public void setTransporter(Transporter transporter) {
        this.transporter = transporter;
    }
}
