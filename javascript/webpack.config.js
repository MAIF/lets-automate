const webpack = require('webpack');
const path = require('path');
const UglifyJsPlugin = require('uglifyjs-webpack-plugin');

module.exports = {
    plugins: [
        new UglifyJsPlugin()
    ]
}

const plugins = [
    new webpack.DefinePlugin({
        '__DEV__': process.env.NODE_ENV === 'production',
        'process.env': {
            NODE_ENV: JSON.stringify(process.env.NODE_ENV || 'dev')
        }
    })
];

if (process.env.NODE_ENV === 'production') {
    plugins.push(new UglifyJsPlugin({}));
} else {
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

    // Enable sourcemaps for debugging webpack's output.
    //devtool: "source-map",

    resolve: {
        // Add '.ts' and '.tsx' as resolvable extensions.
        extensions: [".ts", ".tsx", ".js", ".json", ".css", ".scss"]
    },

    devServer: {
        port: process.env.DEV_SERVER_PORT || 3333
    },

    module: {
        rules: [
            { test: /\.tsx?$/, loader: "awesome-typescript-loader" },
            {
                test: /\.js|\.jsx|\.es6$/,
                exclude: /node_modules/,
                loader: 'babel-loader'
            },
            { enforce: "pre", test: /\.js$/, loader: "source-map-loader" },
            {
                test: /node_modules\/auth0-lock\/.*\.js$/,
                loaders: [
                    'transform-loader/cacheable?brfs',
                    'transform-loader/cacheable?packageify'
                ]
            },
            {
                test: /node_modules\/auth0-lock\/.*\.ejs$/,
                loader: 'transform-loader/cacheable?ejsify'
            },
            {
                test: /\.woff(2)?(\?v=[0-9]\.[0-9]\.[0-9])?$/,
                loader: 'url-loader?limit=10000&minetype=application/font-woff'
            },
            {
                test: /\.(ttf|eot|svg)(\?v=[0-9]\.[0-9]\.[0-9])?$/,
                loader: 'file-loader'
            },
            {
                test: /\.json$/,
                loader: 'json-loader'
            },
            {
                test: /\.scss$/,
                loaders: ['style-loader', 'css-loader', 'sass-loader']
            },
            {
                test: /\.css$/,
                exclude: /\.useable\.css$/,
                loader: 'style-loader!css-loader'
            },
            {
                test: /\.useable\.css$/,
                loader: 'style-loader/useable!css-loader'
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