import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import { initRipple } from "./lib/ripple";
import "./style.css";

// L12-N6: only the Material web modules actually used by the UI are
// registered (each SFC imports what its template renders); the catch-all
// `@material/web/all.js` pulled every component into the initial chunk.
import { styles as typescaleStyles } from "@material/web/typography/md-typescale-styles.js";

if (typescaleStyles.styleSheet) {
  document.adoptedStyleSheets.push(typescaleStyles.styleSheet);
}

initRipple();

createApp(App).use(createPinia()).mount("#app");
