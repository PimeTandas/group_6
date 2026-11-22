package service;

import domains.DockingBay;
import domains.Transporter;
import enums.BayStatus;
import enums.TransportState;

import java.time.LocalDateTime;

public class DockingServiceImpl implements DockingService {

    // Lock order used everywhere to avoid deadlock:
    // transports first (lowest id first), then bays (lowest id first), then maintenance teams.
    // The cargo service also locks the transport so all of them wait for each other.

    @Override
    public void allocateBay(Transporter transport, DockingBay bay) {

        if (transport == null || bay == null) {
            throw new IllegalArgumentException("Transport and bay cannot be null.");
        }

        synchronized (transport) {
            synchronized (bay) {

                // check everything first so nothing changes if something is wrong
                if (transport.getState() != TransportState.WAITING) {
                    throw new IllegalStateException(
                            "Only WAITING transports can dock, state is: " + transport.getState());
                }

                if (transport.getDockingBay() != null) {
                    throw new IllegalStateException("Transport already has a docking bay.");
                }

                if (bay.getStatus() != BayStatus.AVAILABLE) {
                    throw new IllegalStateException(
                            "Bay " + bay.getBayId() + " is not available, it is " + bay.getStatus());
                }

                bay.setStatus(BayStatus.OCCUPIED);
                bay.setOccupant(transport);
                transport.setDockingBay(bay);
                transport.setState(TransportState.DOCKED);
            }
        }
    }

    @Override
    public void exchangeBays(Transporter first, Transporter second) {

        if (first == null || second == null) {
            throw new IllegalArgumentException("Transports cannot be null.");
        }

        if (first.getTransporterId().equals(second.getTransporterId())) {
            throw new IllegalArgumentException("Cannot exchange bays with the same transport.");
        }

        // lock the transports lowest id first, same trick as transferCargo
        Transporter lowTransport = first.getTransporterId().compareTo(second.getTransporterId()) < 0
                ? first : second;
        Transporter highTransport = lowTransport == first ? second : first;

        synchronized (lowTransport) {
            synchronized (highTransport) {

                checkCanExchange(first);
                checkCanExchange(second);

                DockingBay bayOfFirst = first.getDockingBay();
                DockingBay bayOfSecond = second.getDockingBay();

                // then lock the bays lowest id first
                DockingBay lowBay = bayOfFirst.getBayId().compareTo(bayOfSecond.getBayId()) < 0
                        ? bayOfFirst : bayOfSecond;
                DockingBay highBay = lowBay == bayOfFirst ? bayOfSecond : bayOfFirst;

                synchronized (lowBay) {
                    synchronized (highBay) {

                        // all checks passed so now swap both
                        first.setDockingBay(bayOfSecond);
                        second.setDockingBay(bayOfFirst);
                        bayOfFirst.setOccupant(second);
                        bayOfSecond.setOccupant(first);
                    }
                }
            }
        }
    }

    private void checkCanExchange(Transporter transport) {

        TransportState state = transport.getState();

        if (state != TransportState.DOCKED && state != TransportState.READY) {
            throw new IllegalStateException(
                    "Transport " + transport.getTransporterId()
                            + " must be DOCKED or READY to exchange bays, state is: " + state);
        }

        if (transport.getDockingBay() == null) {
            throw new IllegalStateException(
                    "Transport " + transport.getTransporterId() + " has no docking bay.");
        }
    }

    @Override
    public void closeBay(DockingBay bay) {

        if (bay == null) {
            throw new IllegalArgumentException("Bay cannot be null.");
        }

        synchronized (bay) {

            if (bay.getStatus() == BayStatus.OCCUPIED) {
                throw new IllegalStateException("Cannot close occupied bay " + bay.getBayId());
            }

            if (bay.getStatus() == BayStatus.CLOSED) {
                throw new IllegalStateException("Bay " + bay.getBayId() + " is already closed.");
            }

            bay.setStatus(BayStatus.CLOSED);
        }
    }

    @Override
    public void reopenBay(DockingBay bay) {

        if (bay == null) {
            throw new IllegalArgumentException("Bay cannot be null.");
        }

        synchronized (bay) {

            if (bay.getStatus() != BayStatus.CLOSED) {
                throw new IllegalStateException("Bay " + bay.getBayId() + " is not closed.");
            }

            bay.setStatus(BayStatus.AVAILABLE);
        }
    }

    @Override
    public boolean departTransport(Transporter transport, LocalDateTime now) {

        if (transport == null || now == null) {
            throw new IllegalArgumentException("Transport and time cannot be null.");
        }

        synchronized (transport) {

            // checked inside the lock because the transport could have changed
            // since whoever called this looked at it
            if (transport.getState() != TransportState.READY
                    || transport.getDepartureTime().isAfter(now)) {
                return false;
            }

            DockingBay bay = transport.getDockingBay();

            if (bay != null) {
                synchronized (bay) {
                    bay.setOccupant(null);
                    bay.setStatus(BayStatus.AVAILABLE);
                }
            }

            transport.setDockingBay(null);
            transport.setState(TransportState.DEPARTED);
            return true;
        }
    }
}
