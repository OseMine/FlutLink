<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import QRCode from "qrcode";

// #409/#423: QR code for a share link, rendered locally via `qrcode`
// (no third-party API — share URLs never leave the device).
const props = withDefaults(
  defineProps<{
    value: string;
    size?: number;
  }>(),
  { size: 168 }
);

const canvas = ref<HTMLCanvasElement | null>(null);

function render() {
  const el = canvas.value;
  if (!el || !props.value) return;
  void QRCode.toCanvas(el, props.value, { width: props.size, margin: 1 });
}

watch(() => props.value, render, { immediate: true });
onMounted(render);
</script>

<template>
  <canvas ref="canvas" class="mx-auto" :style="{ width: size + 'px', height: size + 'px' }" />
</template>