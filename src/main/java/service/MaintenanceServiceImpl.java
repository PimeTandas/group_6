package service;

import domains.MaintenanceTeam;
import domains.Transporter;
import enums.MaintenanceStatus;
import enums.TransportState;

public class MaintenanceServiceImpl implements MaintenanceService {

    // lock order: transport first, then the team

    @Override
    public void assignTeam(Transporter transport, MaintenanceTeam team) {

        if (transport == null || team == null) {
            throw new IllegalArgumentException("Transport and team cannot be null.");
        }

        synchronized (transport) {
            synchronized (team) {

                if (transport.getState() != TransportState.DOCKED) {
                    throw new IllegalStateException(
                            "Only DOCKED transports can be serviced, state is: " + transport.getState());
                }

                if (transport.getMaintenanceTeam() != null) {
                    throw new IllegalStateException("Transport already has a maintenance team.");
                }

                if (team.getMaintenanceStatus() != MaintenanceStatus.AVAILABLE) {
                    throw new IllegalStateException("Team " + team.getMaintenanceId() + " is busy.");
                }

                team.setMaintenanceStatus(MaintenanceStatus.BUSY);
                team.setTransporter(transport);
                transport.setMaintenanceTeam(team);
                transport.setState(TransportState.SERVICING);
            }
        }
    }

    @Override
    public void completeMaintenance(Transporter transport) {

        if (transport == null) {
            throw new IllegalArgumentException("Transport cannot be null.");
        }

        synchronized (transport) {

            MaintenanceTeam team = transport.getMaintenanceTeam();

            if (transport.getState() != TransportState.SERVICING || team == null) {
                throw new IllegalStateException("Transport is not being serviced.");
            }

            synchronized (team) {
                team.setMaintenanceStatus(MaintenanceStatus.AVAILABLE);
                team.setTransporter(null);
                transport.setMaintenanceTeam(null);
                transport.setState(TransportState.READY);
            }
        }
    }
}
