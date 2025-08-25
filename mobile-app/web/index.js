import { AppRegistry } from 'react-native';
import App from '../App';

// 加载Mermaid库
if (typeof window !== 'undefined') {
  // 动态加载Mermaid库
  const script = document.createElement('script');
  script.src = 'https://cdn.jsdelivr.net/npm/mermaid@10.6.1/dist/mermaid.min.js';
  script.onload = () => {
    // 初始化Mermaid
    if (window.mermaid) {
      window.mermaid.initialize({
        startOnLoad: true,
        theme: 'default',
        flowchart: {
          useMaxWidth: true,
          htmlLabels: true,
          curve: 'basis'
        }
      });
      console.log('Mermaid库加载成功');
    }
  };
  script.onerror = () => {
    console.error('Mermaid库加载失败');
  };
  document.head.appendChild(script);
}

// 注册应用
AppRegistry.registerComponent('StockAnalysisAI', () => App);
AppRegistry.runApplication('StockAnalysisAI', {
  rootTag: document.getElementById('root')
});
