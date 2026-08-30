<script setup>
import { computed } from 'vue'
import sofa1 from '../assets/backgrounds/sofa-1.png'
import sofa2 from '../assets/backgrounds/sofa-2.png'
import sofa3 from '../assets/backgrounds/sofa-3.png'
import sofa4 from '../assets/backgrounds/sofa-4.png'

/**
 * The sofa "banner" that sits between the tabs and the page heading. Shows a
 * cropped slice of the sofa PNG (sofa + popcorn + headroom) with each verified
 * avatar seated on a cushion. Everything is sized relative to this element --
 * no viewport units -- so it renders identically on desktop and every mobile
 * browser, and iOS Safari's collapsing toolbar can't shift it.
 */
const props = defineProps({
  // 1-4: which sofa image, and how many cushions to seat
  count: { type: Number, required: true },
  // avatar image URLs (or null), one per active person, length === count
  avatars: { type: Array, default: () => [] }
})

const SOFA_SRC = { 1: sofa1, 2: sofa2, 3: sofa3, 4: sofa4 }
const sofaSrc = computed(() => SOFA_SRC[props.count] ?? sofa2)

// The sofa PNGs are 1444x1724 on a flat dark ground (#0b0f1c), sofa in the
// vertical middle. The "Us" sofas (2/3/4) are LEFT-anchored -- matching the
// left-hand "Us" tab -- so they grow rightward; the solo armchair (1) is
// RIGHT-anchored under the right-hand "Just Me" tab. WIN is the slice we crop
// to (image pixels); same width + scale for every count, only the horizontal
// offset changes, sized so the 4-seater + popcorn fill the banner.
const IMG_W = 1444
const IMG_H = 1724
const WIN_Y0 = 695
const WIN_Y1 = 1012
const WIN_2PLUS = { x0: 60, x1: 1160 } // sofa hugs the left, ground fills the right
const WIN_SOLO = { x0: 280, x1: 1380 } // armchair hugs the right
const win = computed(() => {
  const { x0, x1 } = props.count === 1 ? WIN_SOLO : WIN_2PLUS
  return { x1, y1: WIN_Y1, w: x1 - x0, h: WIN_Y1 - WIN_Y0 }
})

// Cushion centres as a fraction across the image (from the left). For the "Us"
// sofas the LEFTMOST cushion is fixed and each seat is one cushion-width to its
// right, so 2 -> 3 -> 4 people grow the row rightward.
const SEAT_LAYOUTS = {
  1: [0.839],
  2: [0.162, 0.288],
  3: [0.161, 0.282, 0.403],
  4: [0.161, 0.282, 0.403, 0.523]
}
// Vertical centre of a seated avatar (fraction down the image) -- on the
// cushion, head above the sofa back.
const SEAT_Y = 0.46

function seatStyle(index) {
  const cushions = SEAT_LAYOUTS[props.count] ?? SEAT_LAYOUTS[2]
  const { x1, y1, w, h } = win.value
  return {
    right: `${((x1 - IMG_W * cushions[index]) / w) * 100}%`,
    bottom: `${((y1 - IMG_H * SEAT_Y) / h) * 100}%`
  }
}

// Avatar diameter as a % of the stage width -- smaller as the sofa gets more
// cushions so they don't crowd each other.
const SEAT_WIDTH = { 1: 14.5, 2: 14.5, 3: 14.5, 4: 14.5 }

const imgStyle = computed(() => {
  const { x1, y1, w, h } = win.value
  return {
    width: `${(IMG_W / w) * 100}%`,
    right: `${(-(IMG_W - x1) / w) * 100}%`,
    bottom: `${(-(IMG_H - y1) / h) * 100}%`
  }
})
const stageStyle = computed(() => ({
  aspectRatio: `${win.value.w} / ${win.value.h}`,
  '--seat-w': `${SEAT_WIDTH[props.count] ?? 14}%`
}))
</script>

<template>
  <div class="sofa-stage" :style="stageStyle" aria-hidden="true">
    <img :src="sofaSrc" alt="" class="sofa-img" :style="imgStyle" />
    <template v-for="(url, index) in avatars" :key="index">
      <Transition name="seat-pop">
        <img v-if="url" :src="url" alt="" class="seat" :style="seatStyle(index)" />
      </Transition>
    </template>
  </div>
</template>

<style scoped>
.sofa-stage {
  position: relative;
  width: 100%;
  overflow: hidden;
  margin: 0 0 1.25rem;
}

.sofa-img {
  position: absolute;
  bottom: 0;
  max-width: none;
}

.seat {
  position: absolute;
  width: var(--seat-w, 15%);
  aspect-ratio: 1;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid #fff;
  box-shadow: 0 0.4rem 0.9rem rgba(0, 0, 0, 0.5);
  transform: translate(50%, 50%);
}

/* Drop onto the cushion when the username checks out. */
.seat-pop-enter-active {
  transition: opacity 0.35s ease, transform 0.4s cubic-bezier(0.2, 1.4, 0.4, 1);
}

.seat-pop-leave-active {
  transition: opacity 0.2s ease;
}

.seat-pop-enter-from {
  opacity: 0;
  transform: translate(50%, 140%);
}

.seat-pop-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .seat-pop-enter-active {
    transition: opacity 0.2s ease;
  }
  .seat-pop-enter-from {
    transform: translate(50%, 50%);
  }
}

</style>
