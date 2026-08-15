import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import { initRipple } from "./lib/ripple";
import "./style.css";

initRipple();

createApp(App).use(createPinia()).mount("#app");
