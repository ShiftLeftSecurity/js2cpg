// eslint-disable-next-line @typescript-eslint/no-var-requires
const { defineConfig } = require('@vue/cli-service');
module.exports = defineConfig({
  transpileDependencies: true,
  configureWebpack: {
    devtool: 'source-map',
  }
});
