import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import { initRipple } from "./lib/ripple";
import "./style.css";

import "@material/web/all.js";
import { styles as typescaleStyles } from "@material/web/typography/md-typescale-styles.js";

if (typescaleStyles.styleSheet) {
  document.adoptedStyleSheets.push(typescaleStyles.styleSheet);
}

initRipple();

createApp(App).use(createPinia()).mount("#app");
