package enums;

public enum TransportState {
    WAITING,
    DOCKED,
    SERVICING,
    READY,
    DEPARTED;

    public boolean validTransitionTo(TransportState next){
        return false;
    }
}
