package enums;

public enum TransportState {
    WAITING,
    DOCKED,
    SERVICING,
    READY,
    DEPARTED;

    /***
     * Validates state transition changes
     *
     * @param next
     * @return weather or not a current state transition to the next is valid.
     */
    public boolean validTransitionTo(TransportState next){
        switch(this){
            case WAITING:
                return next == DOCKED;
            case DOCKED:
                return next == SERVICING;
            case SERVICING:
                return next == READY;
            case READY:
                return next == DEPARTED;
            case DEPARTED:
                return false;
        }
        return false;
    }
}
