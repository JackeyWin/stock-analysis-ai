// Mermaid库加载器
class MermaidLoader {
  constructor() {
    this.isLoaded = false;
    this.loadPromise = null;
  }

  // 加载Mermaid库
  async load() {
    if (this.isLoaded) {
      return Promise.resolve();
    }

    if (this.loadPromise) {
      return this.loadPromise;
    }

    this.loadPromise = new Promise((resolve, reject) => {
      // 检查是否已经加载
      if (window.mermaid) {
        this.isLoaded = true;
        this.initialize();
        resolve();
        return;
      }

      // 动态加载Mermaid库
      const script = document.createElement('script');
      script.src = 'https://cdn.jsdelivr.net/npm/mermaid@10.6.1/dist/mermaid.min.js';
      script.async = true;
      
      script.onload = () => {
        if (window.mermaid) {
          this.isLoaded = true;
          this.initialize();
          resolve();
        } else {
          reject(new Error('Mermaid库加载失败'));
        }
      };
      
      script.onerror = () => {
        reject(new Error('Mermaid库加载失败'));
      };

      document.head.appendChild(script);
    });

    return this.loadPromise;
  }

  // 初始化Mermaid配置
  initialize() {
    if (!window.mermaid) return;

    try {
      window.mermaid.initialize({
        startOnLoad: true,
        theme: 'default',
        flowchart: {
          useMaxWidth: true,
          htmlLabels: true,
          curve: 'basis'
        },
        sequence: {
          useMaxWidth: true,
          diagramMarginX: 50,
          diagramMarginY: 10
        },
        gantt: {
          useMaxWidth: true
        }
      });
      
      console.log('✅ Mermaid库初始化成功');
    } catch (error) {
      console.error('❌ Mermaid库初始化失败:', error);
    }
  }

  // 渲染图表
  async render(id, code) {
    await this.load();
    
    if (!window.mermaid) {
      throw new Error('Mermaid库未加载');
    }

    try {
      return new Promise((resolve, reject) => {
        window.mermaid.render(id, code, (svgCode) => {
          resolve(svgCode);
        }, (error) => {
          reject(error);
        });
      });
    } catch (error) {
      throw new Error(`图表渲染失败: ${error.message}`);
    }
  }

  // 检查是否可用
  isAvailable() {
    return this.isLoaded && !!window.mermaid;
  }
}

// 创建全局实例
const mermaidLoader = new MermaidLoader();

// 导出到全局
if (typeof window !== 'undefined') {
  window.mermaidLoader = mermaidLoader;
}

export default mermaidLoader;
