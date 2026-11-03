package com.smartcommerce.finance.dto.response;

import com.smartcommerce.finance.services.CashSessionService;
import java.util.List;
import java.util.UUID;

/**
 * The cash register screen (SC-052): the viewer's role, each drawer with its open session (null
 * when closed) and the accounts a drop can go to or a top-up come from, by name only (no balance).
 */
public record CashOverviewView(String role, List<Drawer> drawers, List<Destination> destinations) {
  public record Drawer(UUID id, String name, CashSessionView session) {}

  public record Destination(UUID id, String name, String type) {}

  public static CashOverviewView of(CashSessionService.Overview overview) {
    return new CashOverviewView(
        overview.role(),
        overview.drawers().stream()
            .map(
                state ->
                    new Drawer(
                        state.drawer().id(),
                        state.drawer().name(),
                        state.session() == null ? null : CashSessionView.of(state.session())))
            .toList(),
        overview.destinations().stream()
            .map(item -> new Destination(item.id(), item.name(), item.type()))
            .toList());
  }
}
