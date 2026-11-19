package service;

import domains.Transporter;
import enums.TransportState;

public class CargoServiceImpl implements CargoService {

    @Override
    public void loadCargo(Transporter transport, int amount) {

        if (transport == null) {
            throw new IllegalArgumentException("Transport cannot be null.");
        }

        synchronized (transport) {

            validateCargoOperation(transport, amount);

            int availableSpace = transport.getMaxCapacity()
                    - transport.getCurrentQuantity();

            if (amount > availableSpace) {
                throw new IllegalArgumentException(
                        "Not enough cargo capacity available.");
            }

            transport.setCurrentQuantity(
                    transport.getCurrentQuantity() + amount);
        }
    }

    @Override
    public void unloadCargo(Transporter transport, int amount) {

        if (transport == null) {
            throw new IllegalArgumentException("Transport cannot be null.");
        }

        synchronized (transport) {

            validateCargoOperation(transport, amount);

            if (amount > transport.getCurrentQuantity()) {
                throw new IllegalArgumentException(
                        "Transport does not contain enough cargo.");
            }

            transport.setCurrentQuantity(
                    transport.getCurrentQuantity() - amount);
        }
    }


    @Override
    public void transferCargo(Transporter source,
                              Transporter destination,
                              int amount) {

        if (source == null || destination == null) {
            throw new IllegalArgumentException(
                    "Transports cannot be null.");
        }

        if (source.getTransporterId().equals(
                destination.getTransporterId())) {
            throw new IllegalArgumentException(
                    "Cannot transfer cargo to the same transport.");
        }

        Transporter first =
                source.getTransporterId().compareTo(
                        destination.getTransporterId()) < 0
                        ? source : destination;

        Transporter second =
                first == source ? destination : source;

        synchronized (first) {
            synchronized (second) {

                validateCargoOperation(source, amount);
                validateCargoOperation(destination, amount);

                if (amount > source.getCurrentQuantity()) {
                    throw new IllegalArgumentException(
                            "Source has insufficient cargo.");
                }

                int availableSpace =
                        destination.getMaxCapacity()
                                - destination.getCurrentQuantity();

                if (amount > availableSpace) {
                    throw new IllegalArgumentException(
                            "Destination has insufficient capacity.");
                }

                source.setCurrentQuantity(
                        source.getCurrentQuantity() - amount);

                destination.setCurrentQuantity(
                        destination.getCurrentQuantity() + amount);
            }
        }
    }

    private void validateCargoOperation(Transporter transport, int amount) {

        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Cargo amount must be greater than zero.");
        }

        TransportState state = transport.getState();

        if (state != TransportState.DOCKED
                && state != TransportState.SERVICING
                && state != TransportState.READY) {

            throw new IllegalStateException(
                    "Cargo operations are not allowed in state: " + state);
        }
    }
}
