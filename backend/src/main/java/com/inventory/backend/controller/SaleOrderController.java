package com.inventory.backend.controller;

import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.dto.CreateSaleOrderRequest;
import com.inventory.backend.dto.SaleOrderDTO;
import com.inventory.backend.service.SaleOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
public class SaleOrderController {

    private final SaleOrderService saleOrderService;

    @GetMapping
    @PreAuthorize("hasAuthority('SALE_READ')")
    public ResponseEntity<ApiResponse<Page<SaleOrderDTO>>> listSales(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(saleOrderService.listSales(pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SALE_CREATE')")
    public ResponseEntity<ApiResponse<SaleOrderDTO>> createSale(@Valid @RequestBody CreateSaleOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Sale recorded successfully", saleOrderService.createSale(request)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SALE_READ')")
    public ResponseEntity<ApiResponse<SaleOrderDTO>> getSale(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(saleOrderService.getSale(id)));
    }

    // ── Void / refund approvals ──────────────────────────────────────────────
    // Requesting is deliberately weaker than approving: a Cashier may ask, a
    // Supervisor or Manager decides. Splitting the two authorities is what makes
    // "cannot void without Supervisor approval" a property of the API rather
    // than a convention the UI is trusted to follow.

    /**
     * The approval queue.
     *
     * <p>Requires an approve authority, not merely a request authority: being
     * able to ask for a void does not entitle anyone to review one. A literal
     * path segment, which Spring ranks above {@code /{id}}, so this is not
     * parsed as a sale id.
     */
    @GetMapping("/approvals")
    @PreAuthorize("hasAuthority('SALE_VOID_APPROVE') or hasAuthority('SALE_REFUND_APPROVE')")
    public ResponseEntity<ApiResponse<Page<SaleOrderDTO>>> listApprovals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Clamped so one request cannot pull the whole sales ledger into memory.
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return ResponseEntity.ok(ApiResponse.ok(
                "Sales awaiting a void or refund decision",
                saleOrderService.listAwaitingApproval(pageable)));
    }

    @PostMapping("/{id}/void-request")
    @PreAuthorize("hasAuthority('SALE_VOID_REQUEST') or hasAuthority('SALE_VOID_APPROVE')")
    public ResponseEntity<ApiResponse<SaleOrderDTO>> requestVoid(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Void requested; awaiting approval",
                saleOrderService.requestVoid(id, body == null ? null : body.get("reason"))));
    }

    @PostMapping("/{id}/void-approve")
    @PreAuthorize("hasAuthority('SALE_VOID_APPROVE')")
    public ResponseEntity<ApiResponse<SaleOrderDTO>> approveVoid(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Sale voided and stock returned",
                saleOrderService.approveVoid(id, body == null ? null : body.get("note"))));
    }

    @PostMapping("/{id}/void-reject")
    @PreAuthorize("hasAuthority('SALE_VOID_APPROVE')")
    public ResponseEntity<ApiResponse<SaleOrderDTO>> rejectVoid(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Void request rejected",
                saleOrderService.rejectVoid(id, body == null ? null : body.get("note"))));
    }

    @PostMapping("/{id}/refund-request")
    @PreAuthorize("hasAuthority('SALE_REFUND_REQUEST') or hasAuthority('SALE_REFUND_APPROVE')")
    public ResponseEntity<ApiResponse<SaleOrderDTO>> requestRefund(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Refund requested; awaiting approval",
                saleOrderService.requestRefund(id, body == null ? null : body.get("reason"))));
    }

    @PostMapping("/{id}/refund-approve")
    @PreAuthorize("hasAuthority('SALE_REFUND_APPROVE')")
    public ResponseEntity<ApiResponse<SaleOrderDTO>> approveRefund(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Sale refunded and stock returned",
                saleOrderService.approveRefund(id, body == null ? null : body.get("note"))));
    }

    @PostMapping("/{id}/refund-reject")
    @PreAuthorize("hasAuthority('SALE_REFUND_APPROVE')")
    public ResponseEntity<ApiResponse<SaleOrderDTO>> rejectRefund(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Refund request rejected",
                saleOrderService.rejectRefund(id, body == null ? null : body.get("note"))));
    }
}