import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SofaStage from './SofaStage.vue'

const AV = (n) => `https://a.ltrbxd.com/resized/avatar/${n}.jpg`

describe('SofaStage', () => {
  it('picks the sofa image for the person count', () => {
    for (const [count, name] of [
      [1, 'sofa-1'],
      [2, 'sofa-2'],
      [3, 'sofa-3'],
      [4, 'sofa-4']
    ]) {
      const wrapper = mount(SofaStage, { props: { count, avatars: [] } })
      expect(wrapper.find('.sofa-img').attributes('src')).toContain(name)
    }
  })

  it('renders one seated avatar per non-null url, in order', () => {
    const wrapper = mount(SofaStage, {
      props: { count: 3, avatars: [AV('a'), AV('b'), AV('c')] }
    })

    const seats = wrapper.findAll('.seat')
    expect(seats).toHaveLength(3)
    expect(seats.map((s) => s.attributes('src'))).toEqual([AV('a'), AV('b'), AV('c')])
  })

  it('leaves a cushion empty for a null avatar without shifting the others', () => {
    const wrapper = mount(SofaStage, {
      props: { count: 3, avatars: [AV('a'), null, AV('c')] }
    })

    const seats = wrapper.findAll('.seat')
    expect(seats).toHaveLength(2)
    expect(seats.map((s) => s.attributes('src'))).toEqual([AV('a'), AV('c')])

    // seat for index 2 keeps its own position (further right) than index 0
    const right0 = parseFloat(seats[0].element.style.right)
    const right2 = parseFloat(seats[1].element.style.right)
    expect(right2).toBeLessThan(right0)
  })

  it('anchors the "Us" sofas left and the solo armchair right', () => {
    const us = mount(SofaStage, { props: { count: 2, avatars: [] } })
    const solo = mount(SofaStage, { props: { count: 1, avatars: [] } })

    const usRight = parseFloat(us.find('.sofa-img').element.style.right)
    const soloRight = parseFloat(solo.find('.sofa-img').element.style.right)

    // both negative offsets, but the left-anchored sofa is pulled much further left
    expect(usRight).toBeLessThan(soloRight)
  })

  it('lays the "Us" cushions out left to right', () => {
    const wrapper = mount(SofaStage, {
      props: { count: 4, avatars: [AV('a'), AV('b'), AV('c'), AV('d')] }
    })
    const rights = wrapper.findAll('.seat').map((s) => parseFloat(s.element.style.right))

    // index 0 is the leftmost cushion -> largest distance from the right edge
    expect(rights).toEqual([...rights].sort((a, b) => b - a))
  })

  it('has no avatars when none are verified', () => {
    const wrapper = mount(SofaStage, { props: { count: 2, avatars: [null, null] } })
    expect(wrapper.findAll('.seat')).toHaveLength(0)
    expect(wrapper.find('.sofa-img').exists()).toBe(true)
  })
})
