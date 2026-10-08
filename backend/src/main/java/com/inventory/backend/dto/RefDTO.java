package com.inventory.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A named reference to something a record points at, rather than a bare id.
 *
 * <p>The alternative — shipping {@code createdById: 7} and leaving the UI to look
 * the name up — is what makes a list screen either show a raw number or perform
 * an N+1 fetch per row to resolve it. Returning the name alongside the id means
 * the row renders from the response it already has.
 *
 * <p>{@code id} is nullable on purpose: not every reference in this schema points
 * at a row with a surrogate key. A user's branch, for instance, is free text on
 * the user record rather than a row in its own table, so there is no id to
 * report. Callers must render {@code name} and treat a null id as normal, not as
 * a broken payload. If a concept that is currently free text later becomes a real
 * entity, its id starts arriving here and no client has to change.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefDTO {

    private Long id;

    private String name;
}
