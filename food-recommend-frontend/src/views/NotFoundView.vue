<template>
  <div class="nf">
    <h1>404</h1>
    <p>页面走丢了～</p>
    <router-link to="/">返回首页</router-link>
  </div>
</template>

<style scoped>
.nf {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: 12px;
}
.nf h1 {
  font-size: 96px;
  margin: 0;
  color: #909399;
}
</style>
