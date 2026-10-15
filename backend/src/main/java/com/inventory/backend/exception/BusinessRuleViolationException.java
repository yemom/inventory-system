package com.inventory.backend.exception;

/**
 * A request that cannot be carried out as asked, but where nothing has gone
 * wrong.
 *
 * <p>This exists so the difference between "the server broke" and "you have not
 * told us enough to do this" survives all the way to the status code. Both used
 * to arrive as {@link IllegalStateException}, which has no handler and therefore
 * became a 500 — so a cashier who had not selected a warehouse was told, in
 * effect, that StockFlow had crashed.
 *
 * <p>The distinction matters to whoever is on the other end of it. A 500 means
 * retrying might help and someone should look at the logs. A 409 means the
 * request needs different input, and it is the caller who has to change it.
 * Blurring the two teaches staff to report working business rules as bugs.
 *
 * <p>Thrown for a state the caller must resolve: no warehouse exists at all,
 * several exist and none was chosen, or stock is not where the transaction
 * claims it is.
 */
public class BusinessRuleViolationException extends RuntimeException {

    private final String code;

    public BusinessRuleViolationException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * A short machine-readable code for the client to branch on.
     *
     * <p>Kept separate from the human message so the wording can be improved
     * without breaking anything that switches on it.
     */
    public String getCode() {
        return code;
    }
}