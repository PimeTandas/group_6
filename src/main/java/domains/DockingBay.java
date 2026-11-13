package domains;

import enums.BayStatus;

public class DockingBay {

    private final String bayId;
    private BayStatus status;
    private Transporter occupant;

    public DockingBay(String bayId, BayStatus status, Transporter occupant) {
        this.bayId = bayId;
        this.status = status;
        this.occupant = occupant;
    }

    public String getBayId() {
        return bayId;
    }

    public BayStatus getStatus() {
        return status;
    }

    public void setStatus(BayStatus status) {
        this.status = status;
    }

    public Transporter getOccupant() {
        return occupant;
    }

    public void setOccupant(Transporter occupant) {
        this.occupant = occupant;
    }
}
