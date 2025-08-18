import { DefaultTheme } from 'react-native-paper';

export const theme = {
  ...DefaultTheme,
  colors: {
    ...DefaultTheme.colors,
    primary: '#2196F3',
    accent: '#FF5722',
    background: '#F5F5F5',
    surface: '#FFFFFF',
    text: '#212121',
    disabled: '#BDBDBD',
    placeholder: '#757575',
    backdrop: 'rgba(0, 0, 0, 0.5)',
    // 股票相关颜色
    profit: '#4CAF50',  // 盈利/上涨 - 绿色
    loss: '#F44336',    // 亏损/下跌 - 红色
    warning: '#FF9800', // 警告 - 橙色
    info: '#2196F3',    // 信息 - 蓝色
  },
  fonts: {
    ...DefaultTheme.fonts,
    regular: {
      fontFamily: 'System',
      fontWeight: 'normal',
    },
    medium: {
      fontFamily: 'System',
      fontWeight: '500',
    },
    light: {
      fontFamily: 'System',
      fontWeight: '300',
    },
    thin: {
      fontFamily: 'System',
      fontWeight: '100',
    },
  },
};

export const styles = {
  container: {
    flex: 1,
    backgroundColor: theme.colors.background,
  },
  card: {
    marginHorizontal: 8,
    marginVertical: 4,
    elevation: 2,
    backgroundColor: theme.colors.surface,
  },
  header: {
    fontSize: 24,
    fontWeight: 'bold',
    color: theme.colors.text,
    marginBottom: 16,
  },
  subHeader: {
    fontSize: 18,
    fontWeight: '500',
    color: theme.colors.text,
    marginBottom: 8,
  },
  text: {
    fontSize: 16,
    color: theme.colors.text,
  },
  smallText: {
    fontSize: 14,
    color: theme.colors.placeholder,
  },
  profitText: {
    color: theme.colors.profit,
    fontWeight: 'bold',
  },
  lossText: {
    color: theme.colors.loss,
    fontWeight: 'bold',
  },
  centerText: {
    textAlign: 'center',
  },
  padding: {
    padding: 16,
  },
  margin: {
    margin: 16,
  },
};
