# experimental implementation of LSP client

## 実行方法

### (1) 準備その１

1. Eclipseのワークスペースを作成し、「importの編成」対象となるJavaファイルを配置するプロジェクトを作成してください。
2. 同プロジェクトに「importの編成」対象のJavaファイルを配置してください。
3. 「Eclipse JDT Language Server」を[ここ](https://download.eclipse.org/jdtls/snapshots/?d)からダウンロードし、任意の場所に展開してください。
  * 1.のEclipseとバージョンを合わせて下さい。

### (2) 準備その２ - 「Eclipse JDT Language Server」を起動してみる
後述する例を参考にjavaコマンドを実行して下さい。ただし、以下のオプションを書き換えてから実行してください。

* `-jar`オプション
  * 3.で展開したファイルの中にplugins/org.eclipse.equinox.launcher_1.5.200.v20180922-1751.jarがあります。ダウンロードした「Eclipse JDT Language Server」のバージョンによってはファイル名の後半が異なっているかもしれません。`-jar`オプションで指定するパス及びファイル名を、必要であればご自身の環境に合わせて修正してください。
* `-configuration`オプション
  * 3.で展開したファイルの中に`config_linux` `config_mac` `config_win`があります。 `-configuration`オプションで指定するパス及びファイル名を、必要であればご自身の環境に合わせて修正してください。
* `-data`オプション
  * 1. で作成した**ワークスペース**へのパスを指定してください。
 
#### javaコマンドの起動引数の例
参考：[Running from the command line](https://github.com/eclipse/eclipse.jdt.ls/blob/master/README.md#running-from-the-command-line) 5.~

* JDK8以前
 ```
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1044 -Declipse.application=org.eclipse.jdt.ls.core.id1 -Dosgi.bundles.defaultStartLevel=4 -Declipse.product=org.eclipse.jdt.ls.core.product -Dlog.level=ALL -noverify -Xmx1G -jar ./plugins/org.eclipse.equinox.launcher_1.5.200.v20180922-1751.jar -configuration ./config_linux -data /path/to/data
```
* JDK9以降
```
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1044 -Declipse.application=org.eclipse.jdt.ls.core.id1 -Dosgi.bundles.defaultStartLevel=4 -Declipse.product=org.eclipse.jdt.ls.core.product -Dlog.level=ALL -noverify -Xmx1G -jar ./plugins/org.eclipse.equinox.launcher_1.5.200.v20180922-1751.jar -configuration ./config_linux -data /path/to/data --add-modules=ALL-SYSTEM --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED
```
### 注意
「Eclipse JDT Language Server」を起動すると、以下のようなメッセージが表示されますがすぐに終了すると思います。ですが、この段階では正しい動作ですので次の手順へ進んでください。
```
Listening for transport dt_socket at address: 1044
```

### (3) 準備その３ - LSPClient.javaの修正
このリポジトリのLSPClient.javaの以下の箇所を「(1) 準備その１」で配置したJavaファイルまで辿れるよう修正してください。
```
    // Path to target files
    private static final Path WORKSPACE = Paths.get("C:\\Eclipse\\Workspace");
    private static final Path PROJECT_NAME = Paths.get("sample");
    private static final Path PATH_TO_SRC = Paths.get("src\\main\\java");
    private static final Path PACKAGE = Paths.get("test.sample".replace('.', '/'));
```

### (4) LSPClientと「Eclipse JDT Language Server」の起動

1. 修正したLSPClient.javaをコンパイルし実行してください。
2. LSPClientの起動を確認したら、再度「Eclipse JDT Language Server」を実行してください。
3. クライアントのコンソールに、「(1) 準備その１」で配置したJavaファイル名を入力してください。クライアントからのリクエストとサーバからの応答が表示されます。
4. クライアントのコンソールに`exit`と入力すると、LSPClientと「Eclipse JDT Language Server」が終了します。

