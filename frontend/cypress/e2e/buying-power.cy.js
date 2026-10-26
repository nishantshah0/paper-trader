// Open limit orders hold buying power and shares; these check the terminal shows it.
describe("buying power", () => {
  it("a resting buy limit holds its cash until it is cancelled", () => {
    cy.newAccount().then((account) => {
      cy.openTerminal(account.id);
      cy.buyingPower().should("have.text", "$100,000.00");

      cy.get("#order-type").select("LIMIT");
      cy.get("#quantity").clear().type("1000");
      cy.get("#limit").clear().type("1");
      cy.contains("button", "Buy AAPL").click();
      cy.get('[role="status"]').should("contain.text", "open");
      cy.buyingPower().should("have.text", "$99,000.00");

      cy.contains("button", /Cancel #/).click();
      cy.get('[role="status"]').should("contain.text", "cancelled");
      cy.buyingPower().should("have.text", "$100,000.00");
    });
  });

  it("refuses a buy that needs cash already held by an open order", () => {
    cy.newAccount().then((account) => {
      // hold all but $500 of the account behind a limit that will not cross
      cy.request("POST", `/api/accounts/${account.id}/orders`, {
        symbol: "AAPL",
        side: "BUY",
        type: "LIMIT",
        quantity: 99500,
        limitPrice: 1,
      });
      cy.openTerminal(account.id);
      cy.buyingPower().should("have.text", "$500.00");

      // ten shares fit the account's cash, just not what is left after the hold
      cy.get("#quantity").clear().type("10");
      cy.contains("button", "Buy AAPL").click();
      cy.get('[role="alert"]').should("contain.text", "insufficient cash");
      cy.buyingPower().should("have.text", "$500.00");
    });
  });

  it("shares on an open sell limit cannot be sold twice", () => {
    cy.newAccount().then((account) => {
      const orders = `/api/accounts/${account.id}/orders`;
      cy.request("POST", orders, { symbol: "AAPL", side: "BUY", quantity: 2 });
      cy.request("POST", orders, {
        symbol: "AAPL",
        side: "SELL",
        type: "LIMIT",
        quantity: 2,
        limitPrice: 999999,
      });
      cy.openTerminal(account.id);

      cy.contains("button", /^Sell$/).click();
      cy.contains("span", "Shares available")
        .next("b")
        .should("have.text", "0");
      cy.contains("button", "Sell AAPL").click();
      cy.get('[role="alert"]').should("contain.text", "insufficient shares");
    });
  });
});
