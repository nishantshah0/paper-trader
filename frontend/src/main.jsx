import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App.jsx";
import Home from "./Home.jsx";
import "./style.css";
import "./home.css";
const terminal = /^\/terminal\/?$/.test(window.location.pathname);
document.title = terminal
  ? "PaperTrader — Trading terminal"
  : "PaperTrader — Practice trading with virtual funds";
createRoot(document.getElementById("root")).render(
  terminal ? <App /> : <Home />,
);
