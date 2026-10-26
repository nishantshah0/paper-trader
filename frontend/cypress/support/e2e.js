// A fresh account per test keeps the balances in each assertion obvious.
Cypress.Commands.add("newAccount", () =>
  cy
    .request("POST", "/api/accounts", {
      username:
        "cy_" + Date.now() + "_" + Math.random().toString(36).slice(2, 6),
    })
    .its("body"),
);

Cypress.Commands.add("openTerminal", (id) => {
  cy.visit("/terminal", {
    onBeforeLoad(win) {
      win.localStorage.setItem("paper-trader.account", String(id));
    },
  });
  cy.contains("Account #" + id).should("be.visible");
  cy.contains("Connected").should("be.visible");
});

Cypress.Commands.add("buyingPower", () =>
  cy.contains(".account-strip .metric", "Buying power").find("strong"),
);
