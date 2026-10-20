# ENTREGA — Cancelación de citas con motivo

## 1. Archivos

**Creados**

- `sql/002-motivo-cancelacion.sql`: agrega `citas.motivo_cancelacion VARCHAR(200) NULL` solo si la tabla existe y la columna no. Se puede ejecutar más de una vez. No borra filas.
- `src/main/java/com/medisalud/exception/CancelacionTardiaException.java`: excepción comprobada para una cita con fecha anterior a hoy (RN3).

**Modificados**

- `sql/medisalud.sql`: una instalación nueva crea la columna nullable. Los `INSERT` de ejemplo no la rellenan, así que las citas ya canceladas quedan con motivo `NULL`.
- `src/main/java/com/medisalud/entity/Cita.java`: campo `motivoCancelacion`, validación del motivo, `cancelar(String)` (RN1 y `EstadoCita.puedeTransicionarA` antes de modificar nada), constructor de reconstrucción que no notifica, `reponerEstado` si el guardado falla.
- `src/main/java/com/medisalud/persistencia/jdbc/CitaDAO.java`: el motivo entra en el `INSERT`, en el `ON DUPLICATE KEY UPDATE` y en todos los `SELECT`. `mapearCita` usa el constructor de reconstrucción.
- `src/main/java/com/medisalud/service/ServicioCitas.java`: `cancelarCita(codigo, motivo)`, reporte `cancelacionesPorMedico` y registro del observador para citas leídas de MySQL.
- `src/main/java/com/medisalud/controlador/ControladorMediSalud.java`: delega la cancelación y el reporte. No valida ni ejecuta SQL.
- `src/main/java/com/medisalud/vista/VistaConsola.java`: opción 7 Cancelar, opción 8 Salir, columna Motivo y sección «Cancelaciones por médico».
- `src/main/java/com/medisalud/Principal.java`: registra el observador en `ServicioCitas`.
- `README.md`: menú de 8 opciones y cómo aplicar la migración si la base ya existía.

## 2. Decisión sobre `cancelarCita(String)`

Se eliminó. Nadie lo llamaba y cancelaba sin motivo, así que rompía RN1. La única operación pública queda `cancelarCita(String codigo, String motivo)`.

## 3. Dónde aplica cada concepto

- **State:** `EstadoCita.puedeTransicionarA`, llamado desde `ServicioCitas.cancelarCita` y desde `Cita.cancelar` antes de asignar motivo o estado. El `switch` de transiciones no se copió.
- **Observer:** `Cita.cambiarEstado` avisa a `ObservadorCitaNotificacion`. `ServicioCitas` conecta el observador y llama a `cambiarEstado` solo después de que `guardar` termina bien. `Principal` registra ese observador. Leer o reconstruir una cita no llama a `cambiarEstado`.
- **Excepción propia:** `CancelacionTardiaException`, lanzada en `ServicioCitas.cancelarCita` cuando `fecha.isBefore(LocalDate.now())`.
- **PreparedStatement:** `CitaDAO.guardar`, `buscarPorCodigo` y `listarConFiltro`. Los valores van como parámetros.
- **Stream API:** `ServicioCitas.cancelacionesPorMedico` (`filter`, `groupingBy`, `counting`, y un segundo `stream` para ordenar de mayor a menor y, en empate, por código de médico ascendente).

Si `guardar` lanza `RuntimeException`, el servicio devuelve el objeto al estado y al motivo anteriores con `reponerEstado` y no notifica. El DAO no guarda una copia en memoria: la siguiente lectura sale de MySQL. Limitación: cada método JDBC abre su conexión con autocommit; no hay una transacción que una el `UPDATE` y el aviso.

## 4. Casos de prueba

Las filas `ZP*` se crearon solo para probar y se borraron al terminar. Quedaron 30 citas. Las cinco canceladas de ejemplo (`C004`, `C010`, `C016`, `C022`, `C028`) siguen con motivo `NULL`.

| Caso | Estado | Qué se comprobó |
|---|---|---|
| 1. Pendiente futura, motivo válido, notificación | Ejecutado | Memoria (`A1`), MySQL (`ZP01`) y menú (`ZP07`). El hilo imprimió `[notificacion] La cita ZP07 ahora esta CANCELADA` y el proceso terminó con código 0. |
| 2. Confirmada futura conserva el motivo | Ejecutado | Memoria (`A2`) y MySQL (`ZP02`, motivo `motivo zp02`). |
| 3. Atendida se rechaza y no cambia | Ejecutado | `TransicionInvalidaException` en memoria (`A3`) y en MySQL (`ZP03`). El estado siguió `ATENDIDA` y el motivo `NULL`. |
| 4. Ya cancelada no pisa el motivo | Ejecutado | `TransicionInvalidaException` en memoria (`A4`) y en MySQL (`ZP04`). El motivo siguió siendo `motivo original`. |
| 5. Fecha pasada | Ejecutado | `CancelacionTardiaException` en memoria y en MySQL. Mensaje real: `La cita ZP05 no se puede cancelar: su fecha (2026-10-17) ya paso`. La cita siguió `PENDIENTE`. Una cita con fecha de hoy sí se canceló (memoria). |
| 6. Código inexistente y el programa sigue | Ejecutado | `CitaNoEncontradaException` en memoria, en MySQL y en el menú (`NO-EXISTE-99`). El menú volvió a mostrarse y la opción 8 cerró con código 0. |
| 7. Motivo vacío o solo espacios | Ejecutado | `IllegalArgumentException` en memoria y en MySQL (`ZP07` con `"   "`). El estado siguió `PENDIENTE`. |
| 8. Motivo de 201 caracteres | Ejecutado | Rechazado en memoria y en MySQL. En memoria, 200 caracteres sí se aceptaron. La cita rechazada no cambió de estado. |
| 9. El motivo sigue en MySQL al abrir de nuevo | Ejecutado | Un segundo proceso `java` leyó `ZP01` como `CANCELADA` / `motivo de prueba zp01`. Después se borró esa fila de prueba. |
| 10. `C004` cancelada sin motivo | Ejecutado | El DAO la lee con motivo `NULL`. El menú, opción 6 y médico `M010`, imprimió `Sin motivo registrado`. `C017` y `C030` dejaron la columna vacía. |
| 11. Reporte ordenado como MySQL | Ejecutado | Antes de crear filas de prueba, el servicio devolvió `{M004=1, M006=1, M007=1, M008=1, M010=1}`. El mismo orden salió en el menú y en `GROUP BY` de MySQL. |
| 12. Opción 8 detiene el hilo | Ejecutado | Tres arranques reales terminaron con código 0 y el texto de cierre. En el arranque que canceló `ZP07`, el aviso del hilo se imprimió antes de que el proceso acabara (`detener` + `join`). |

También se listaron las 30 citas con el hilo de notificaciones en marcha y no apareció ningún `[notificacion]`. Un repositorio que falla al guardar dejó la cita en `PENDIENTE`, sin motivo y sin aviso.

## 5. Riesgo / mejora

`CitaDAO.guardar` hace un `INSERT ... ON DUPLICATE KEY UPDATE` sin condicionar el estado anterior. Dos cancelaciones simultáneas de la misma cita pueden pisarse. Un `UPDATE ... WHERE estado IN ('PENDIENTE','CONFIRMADA')` atómico rechazaría la segunda en la base y no solo en memoria.
