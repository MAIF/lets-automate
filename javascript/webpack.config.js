const webpack = require('webpack');
const path = require('path');

const isDev = process.env.NODE_ENV !== "production";

const plugins = [
  new webpack.DefinePlugin({
    '__DEV__': process.env.NODE_ENV === 'production',
    'process.env': {
      NODE_ENV: JSON.stringify(process.env.NODE_ENV || 'development')
    }
  })
];

if (isDev) {
  plugins.push(new webpack.HotModuleReplacementPlugin());
  plugins.push(new webpack.NoEmitOnErrorsPlugin());
}


module.exports = {
  mode: process.env.NODE_ENV,
  entry: {
    LetsAutomate: "./src/index.tsx"
  },
  output: {
    publicPath: '/assets/bundle/',
    path: path.resolve(__dirname, '../src/main/resources/public/bundle/'),
    filename: '[name].js',
    library: '[name]',
    libraryTarget: 'umd'
  },

  devtool : isDev ? "inline-source-map" : false,

  resolve: {
    // Add '.ts' and '.tsx' as resolvable extensions.
    extensions: [".ts", ".tsx", ".js", ".json", ".css", ".scss"]
  },

  devServer: {
    port: 3336
  },

  module: {
    rules: [
      {test: /\.tsx?$/, use: "ts-loader", exclude: "/node_modules/"},
      {
        test: /\.js|\.jsx|\.es6$/,
        exclude: /node_modules/,
        use: ['babel-loader']
      },
      {enforce: "pre", test: /\.js$/, loader: "source-map-loader"},
      {
        test: /node_modules\/auth0-lock\/.*\.js$/,
        use: [
          'transform-loader/cacheable?brfs',
          'transform-loader/cacheable?packageify'
        ]
      },
      {
        test: /node_modules\/auth0-lock\/.*\.ejs$/,
        use: ['transform-loader/cacheable?ejsify']
      },
      {
        test: /\.(ttf|eot|svg)(\?v=[0-9]\.[0-9]\.[0-9])?$/,
        use: ['file-loader']
      },
      {
        test: /\.json$/,
        use: ['json-loader']
      },
      {
        test: /\.scss$/,
        use: ['style-loader', 'css-loader', 'sass-loader']
      },
      {
        test: /\.css$/,
        exclude: /\.useable\.css$/,
        use: ["style-loader", "css-loader"]
      },
      {
        test: /\.useable\.css$/,
        use: ["style-loader/useable", "css-loader"]
      }
    ]
  },

  plugins: plugins,

  // When importing a module whose path matches one of the following, just
  // assume a corresponding global variable exists and use that instead.
  // This is important because it allows us to avoid bundling all of our
  // dependencies, which allows browsers to cache those libraries between builds.
  // externals: {
  //     "react": "React",
  //     "react-dom": "ReactDOM"
  // }
};