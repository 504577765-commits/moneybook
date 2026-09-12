import { createRouter, createWebHashHistory } from 'vue-router'
import Home from '../views/Home.vue'
import Records from '../views/Records.vue'
import Stats from '../views/Stats.vue'
import Me from '../views/Me.vue'
import Widget from '../views/Widget.vue'

const routes = [
  { path: '/', component: Home, name: 'home' },
  { path: '/records', component: Records, name: 'records' },
  { path: '/stats', component: Stats, name: 'stats' },
  { path: '/me', component: Me, name: 'me' },
  { path: '/widget', component: Widget, name: 'widget' }
]

export default createRouter({
  history: createWebHashHistory(),
  routes
})
