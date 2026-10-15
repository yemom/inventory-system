package com.inventory.backend.service;

/**
 * What to do when a withdrawal would take stock below zero.
 *
 * <p>Two different situations, deliberately handled differently, because
 * "stock cannot go negative" has two meanings that need opposite answers.
 *
 * <h2>{@link #REFUSE} — operational movements</h2>
 *
 * <p>A sale, a transfer out, an issue, a supplier return. There is no reading
 * under which any of these may proceed against stock that is not there. A sale
 * recorded against goods that were never received is phantom revenue, and no
 * later reconciliation can tell it apart from a real one. The caller is told what
 * is actually on the shelf and nothing is written.
 *
 * <h2>{@link #CLAMP_AT_ZERO} — an approved count</h2>
 *
 * <p>An adjustment or stock count that a Supervisor has explicitly approved. The
 * approver has looked at the shelf and is signing a correction that says the
 * quantity is lower than the system believed. Refusing would leave the request
 * permanently un-approvable while the shelf sits there wrong — the worse outcome.
 *
 * <p>The clamp is not silent: the movement row carries the quantity that was
 * actually applied, so the ledger still sums to the quantity on hand, and its
 * notes record what was asked for and why less was possible. The difference stays
 * visible, which is the point of recording it at all.
 *
 * <p>An operational movement that needed this treatment would instead record a
 * new ADJUSTMENT explaining the discrepancy. That is the correct instrument for a
 * gap discovered after the fact, and it is what
 * {@code AnomalyDetectionService} looks for.
 */
public enum StockShortfallPolicy {

    /** Refuse the movement. Nothing is written; the caller is told what is available. */
    REFUSE,

    /**
     * Apply as much as the warehouse holds, up to zero, and record the difference
     * on the movement. Only for a correction a human has explicitly approved.
     */
    CLAMP_AT_ZERO
}