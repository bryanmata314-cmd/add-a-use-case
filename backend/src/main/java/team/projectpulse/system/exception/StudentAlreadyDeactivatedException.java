package team.projectpulse.system.exception;

/**
 * UC-STU-deactivate-student extension 6a: the student was deactivated after the caller viewed her details.
 * Mapped to {@code 409 CONFLICT}; the account is left unchanged.
 */
public class StudentAlreadyDeactivatedException extends RuntimeException {

    public StudentAlreadyDeactivatedException() {
        super("This student is already deactivated.");
    }

}
