package com.medisalud.entity;

import com.medisalud.exception.TransicionInvalidaException;
import com.medisalud.notificacion.ObservadorCita;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Cita implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final int LONGITUD_MAXIMA_MOTIVO = 200;

    private final String codigo;
    private final Paciente paciente;
    private final Medico medico;
    private final LocalDate fecha;
    private EstadoCita estado;
    private String motivoCancelacion;
    private transient List<ObservadorCita> observadores = new ArrayList<>();

    public Cita(String codigo, Paciente paciente, Medico medico, LocalDate fecha) {
        this.codigo = codigo;
        this.paciente = paciente;
        this.medico = medico;
        this.fecha = fecha;
        this.estado = EstadoCita.PENDIENTE;
    }

    /** Reconstruye una cita ya persistida: no dispara notificaciones ni valida transiciones. */
    public Cita(String codigo, Paciente paciente, Medico medico, LocalDate fecha,
                EstadoCita estado, String motivoCancelacion) {
        this(codigo, paciente, medico, fecha);
        this.estado = estado;
        this.motivoCancelacion = motivoCancelacion;
    }

    /**
     * RN1, sin modificar la cita. Lo usa el servicio antes de tocar fecha o estado,
     * para que un motivo invalido no llegue a cambiar nada.
     */
    public static void validarMotivoCancelacion(String motivo) {
        normalizarMotivo(motivo);
    }

    public void agregarObservador(ObservadorCita observador) {
        if (!observadores.contains(observador)) {
            observadores.add(observador);
        }
    }

    public void cambiarEstado(EstadoCita nuevoEstado) {
        this.estado = nuevoEstado;
        for (ObservadorCita observador : observadores) {
            observador.notificarCambioEstado(this);
        }
    }

    public void cancelar(String motivo) throws TransicionInvalidaException {
        String motivoLimpio = normalizarMotivo(motivo);
        if (!estado.puedeTransicionarA(EstadoCita.CANCELADA)) {
            throw new TransicionInvalidaException(codigo, estado, EstadoCita.CANCELADA);
        }
        this.motivoCancelacion = motivoLimpio;
        this.estado = EstadoCita.CANCELADA;
    }

    /**
     * Deshace la cancelacion en este objeto si el repositorio no llego a guardarla.
     * No notifica: el cambio nunca quedo persistido.
     */
    public void reponerEstado(EstadoCita estadoAnterior, String motivoAnterior) {
        this.estado = estadoAnterior;
        this.motivoCancelacion = motivoAnterior;
    }

    private static String normalizarMotivo(String motivo) {
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new IllegalArgumentException("El motivo de cancelacion es obligatorio");
        }
        String motivoLimpio = motivo.trim();
        if (motivoLimpio.length() > LONGITUD_MAXIMA_MOTIVO) {
            throw new IllegalArgumentException("El motivo de cancelacion no puede superar "
                    + LONGITUD_MAXIMA_MOTIVO + " caracteres (recibidos: " + motivoLimpio.length() + ")");
        }
        return motivoLimpio;
    }

    public String getCodigo() {
        return codigo;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public Medico getMedico() {
        return medico;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public EstadoCita getEstado() {
        return estado;
    }

    /** Devuelve null si la cita no tiene motivo (nunca se cancelo o se cancelo antes de existir el motivo). */
    public String getMotivoCancelacion() {
        return motivoCancelacion;
    }

    @Override
    public String toString() {
        return "Cita " + codigo + " [" + estado + "] " + paciente.getNombreCompleto()
                + " con " + medico.getNombreCompleto() + " el " + fecha;
    }

    private void readObject(ObjectInputStream entrada) throws IOException, ClassNotFoundException {
        entrada.defaultReadObject();
        observadores = new ArrayList<>();
    }
}
