<script setup lang="ts">
import guanbi from '@/static/icon/guanbi.svg'

defineProps<{ show: boolean; title: string; width?: number }>()
const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <Teleport to="body">
    <div v-if="show" class="overlay" @click.self="emit('close')">
      <div class="modal" :style="width ? { width: width + 'px' } : undefined">
        <button class="modal-close" @click="emit('close')">
          <img :src="guanbi" class="icon-close" alt="" />
        </button>
        <h3>{{ title }}</h3>
        <slot />
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0,0,0,.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.modal {
  background: var(--bg-card, #fff);
  border-radius: var(--radius-lg, 12px);
  padding: 28px 32px;
  width: 480px;
  max-width: 90vw;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 8px 30px rgba(0,0,0,.15);
  position: relative;
}
.modal h3 {
  margin: 0 0 20px;
  font-size: 18px;
  color: var(--text-primary, #333);
}
.modal-close {
  position: absolute;
  top: 16px;
  right: 20px;
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  line-height: 1;
}
.icon-close {
  width: 16px;
  height: 16px;
  display: block;
  opacity: .5;
  transition: opacity .2s;
}
.icon-close:hover {
  opacity: .8;
}
</style>
