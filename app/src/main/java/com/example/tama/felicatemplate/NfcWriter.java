package com.example.tama.felicatemplate;

import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.NfcF;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Created by tama on 16/09/22.
 */

// 参考
// 0次発行FeliCa LiteにNDEFを書き込む
// http://d.hatena.ne.jp/tomorrowkey/20121207/1354894370
// https://github.com/tomorrowkey/FeliCaLiteWriter/blob/master/src/jp/tomorrowkey/android/felicalitewriter/felicalite/FeliCaLiteTag.java

public class NfcWriter {

    private static final String TAG = "NFC_F_sample";
    private static final int ONE_BLOCK_SIZE = 16;


    /**
     * 指定したブロックにデータを書き込む
     * @param targetSystemCode  byte[] 指定するシステムコード
     * @param targetServiceCode byte[] 書き込む対象のサービスコード
     * @return 取得データ
     * @throws IOException
     */
    public boolean writeTag(Tag tag, byte[] targetSystemCode, byte[] targetServiceCode, byte[] data) {
        NfcF nfc = NfcF.get(tag);
        try {
            nfc.connect();
            // System code -> 0x04B8
//            byte[] targetSystemCode = new byte[]{(byte) 0x04,(byte) 0xb8};

            // polling コマンドを作成
            byte[] polling = polling(targetSystemCode);

            // コマンドを送信して結果を取得
            byte[] pollingRes = nfc.transceive(polling);

            // System のIDｍを取得(1バイト目はデータサイズ、2バイト目はレスポンスコード、IDmのサイズは8バイト)
            byte[] targetIDm = Arrays.copyOfRange(pollingRes, 2, 10);

            // サービスに含まれているデータのサイズ(16byteで1とする)
            // Liteは4までしか
            // Standardはサービスに設定されているブロック数以下まで読み込める
            // 学生証のサービス0x7A49のブロック数は12個
            int blockSize = data.length/ONE_BLOCK_SIZE;

            // 対象のサービスコード -> 0x104B
//            byte[] targetServiceCode = new byte[]{(byte) 0x10, (byte) 0x4b};

            // Read Without Encryption コマンドを作成
            byte[] req = writeWithoutEncryption(targetIDm, 0, blockSize, targetServiceCode, data);

            // コマンドを送信して結果を取得
            byte[] res = nfc.transceive(req);

            nfc.close();
            Log.d(TAG,bin2hex(res));

            // レスポンスデータから成功したか確認
            return isResCheck(res);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage() , e);
        }

        return false;
    }

    /**
     * 指定したブロックにデータを書き込む
     * @param blockNumber 書き込みを開始するブロック番号
     * @param targetSystemCode  byte[] 指定するシステムコード
     * @param targetServiceCode byte[] 書き込む対象のサービスコード
     * @return 取得データ
     * @throws IOException
     */
    public boolean writeTag(Tag tag, int blockNumber, byte[] targetSystemCode, byte[] targetServiceCode, byte[] data) {
        NfcF nfc = NfcF.get(tag);
        try {
            nfc.connect();
            // System code -> 0x04B8
//            byte[] targetSystemCode = new byte[]{(byte) 0x04,(byte) 0xb8};

            // polling コマンドを作成
            byte[] polling = polling(targetSystemCode);

            // コマンドを送信して結果を取得
            byte[] pollingRes = nfc.transceive(polling);

            // System のIDｍを取得(1バイト目はデータサイズ、2バイト目はレスポンスコード、IDmのサイズは8バイト)
            byte[] targetIDm = Arrays.copyOfRange(pollingRes, 2, 10);

            // サービスに含まれているデータのサイズ(16byteで1とする)
            // Liteは4までしか
            // Standardはサービスに設定されているブロック数以下まで読み込める
            // 学生証のサービス0x7A49のブロック数は12個
            int blockSize = data.length/ONE_BLOCK_SIZE;

            // 対象のサービスコード -> 0x104B
//            byte[] targetServiceCode = new byte[]{(byte) 0x10, (byte) 0x4b};

            // Read Without Encryption コマンドを作成
            byte[] req = writeWithoutEncryption(targetIDm, blockNumber, blockSize, targetServiceCode, data);

            // コマンドを送信して結果を取得
            byte[] res = nfc.transceive(req);

            nfc.close();
            Log.d(TAG,bin2hex(res));

            // レスポンスデータから成功したか確認
            return isResCheck(res);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage() , e);
        }

        return false;
    }

    /**
     * Pollingコマンドの取得。
     * @param systemCode byte[] 指定するシステムコード
     * @return Pollingコマンド
     * @throws IOException
     */
    private byte[] polling(byte[] systemCode) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream(100);

        bout.write(0x00);           // データ長バイトのダミー
        bout.write(0x00);           // コマンドコード
        bout.write(systemCode[0]);  // systemCode
        bout.write(systemCode[1]);  // systemCode
        bout.write(0x01);           // リクエストコード
        bout.write(0x0f);           // タイムスロット

        byte[] msg = bout.toByteArray();
        msg[0] = (byte) msg.length; // 先頭１バイトはデータ長
        return msg;
    }

    /**
     * Write Without Encryptionコマンドを発行します<br>
     * FeliCa Liteなので、1度のコマンド発行で1ブロックだけ書き込めます p75
     *
     * @param idm IDm
     * @param blockNumber 書き込みの開始番地
     * @param blockSize 書き込むブロック数
     * @param serviceCode 書き込むサービスコード
     * @param data 書き込みデータ
     * @return レスポンス
     * @throws TagLostException
     * @throws IOException
     */
    public byte[] writeWithoutEncryption(byte[] idm, int blockNumber, int blockSize, byte[] serviceCode, byte[] data)
            throws IOException {

        ByteArrayOutputStream bout = new ByteArrayOutputStream(100);

        bout.write(0);              // データ長バイトのダミー
        bout.write(0x08);           // コマンドコード
        bout.write(idm);            // IDm 8byte
        // Liteは1のみ。Standardは1-16まで
        // サービスは1つづつ書き込めばよいので、一つづつ行う
        bout.write(1);              // サービス数の長さ(以下２バイトがこの数分繰り返す)

        // サービスコードの指定はリトルエンディアンなので、下位バイトから指定します。
        bout.write(serviceCode[1]); // サービスコード下位バイト
        bout.write(serviceCode[0]); // サービスコード上位バイト
        bout.write(blockSize);           // ブロック数

        // ブロック番号の指定
        // ブロックリスト
        // 長さ 2Byteなので1b
        // アクセスモード R/Wアクセスで000bに固定
        // サービスコード順番 0000bから開始
        // ブロック番号 引数から書き込み開始番地指定(サイクリックサービスであるときは0から)
        for (int i = blockNumber; i < blockSize; i++) {
            bout.write(0x80);       // ブロックエレメント上位バイト 「Felicaユーザマニュアル抜粋」の4.3項参照
            bout.write(i);          // ブロック番号
        }

        // 書き込みデータ
        bout.write(data);

        byte[] msg = bout.toByteArray();
        msg[0] = (byte) msg.length; // 先頭１バイトはデータ長
        return msg;
    }
    /**
     * Write Without Encryption応答の解析。
     * @param res boolean
     * @return 文字列表現
     * @throws Exception
     */
    private boolean isResCheck(byte[] res) throws Exception {
        // res[11] エラーコード。0x00の場合が正常
        if (res[11] != 0x00)
            throw new RuntimeException("Write Without Encryption Command Error. error code : " + Integer.toHexString(0xff & res[11]));
        return true;
    }
    public static String bin2hex(byte[] data) {
        StringBuffer sb = new StringBuffer();
        for (byte b : data) {
            String s = Integer.toHexString(0xff & b);
            if (s.length() == 1) {
                sb.append("0");
            }
            sb.append(s);
        }
        return sb.toString();
    }
}
