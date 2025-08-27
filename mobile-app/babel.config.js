module.exports = function(api) {
  api.cache(true);
  return {
    presets: ['babel-preset-expo'],
    plugins: [
      // 为了兼容性，暂时不添加reanimated插件
      // 'react-native-reanimated/plugin',
      ['module-resolver', {
        alias: {
          'react-native$': 'react-native-web',
        },
      }],
    ],
  };
};
