package com.example.tama.felicatemplate;

/**
 * Created by tama on 16/09/22.
 */

import android.nfc.Tag;
import android.nfc.tech.NfcF;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class NfcReader {

    private static final String TAG = "NFC_F_sample";

    /**
     * 指定したブロックからデータを読み出す
     * @param targetSystemCode  byte[] 指定するシステムコード
     * @param targetServiceCode byte[] 読み出す対象のサービスコード
     * @return 取得データ
     * @throws IOException
     */
    public byte[][] readTag(Tag tag, byte[] targetSystemCode, byte[] targetServiceCode) {
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
            // Liteは4までしか読み込めない
            // Standardはサービスに設定されているブロック数以下まで読み込める
            // 学生証のサービス0x7A49のブロック数は12個
            int size = 12;

            // 対象のサービスコード -> 0x104B
//            byte[] targetServiceCode = new byte[]{(byte) 0x10, (byte) 0x4b};

            // Read Without Encryption コマンドを作成
            byte[] req = readWithoutEncryption(targetIDm, size, targetServiceCode);

            // コマンドを送信して結果を取得
            byte[] res = nfc.transceive(req);

            nfc.close();

            // 結果をパースしてデータだけ取得
            return parse(res);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage() , e);
        }

        return null;
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
     * Read Without Encryptionコマンドの取得。
     * @param IDm 指定するシステムのID
     * @param size 取得するデータの数
     * @param serviceCode 取得するサービスコード
     * @return Read Without Encryptionコマンド
     * @throws IOException
     */
    private byte[] readWithoutEncryption(byte[] idm, int size, byte[] serviceCode) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream(100);

        bout.write(0);              // データ長バイトのダミー
        bout.write(0x06);           // コマンドコード
        bout.write(idm);            // IDm 8byte
        // Liteは1のみ。Standardは1-16まで
        bout.write(1);              // サービス数の長さ(以下２バイトがこの数分繰り返す)

        // サービスコードの指定はリトルエンディアンなので、下位バイトから指定します。
        bout.write(serviceCode[1]); // サービスコード下位バイト
        bout.write(serviceCode[0]); // サービスコード上位バイト
        bout.write(size);           // ブロック数

        // ブロック番号の指定
        // ブロックリスト
        // 長さ 2Byteなので1b
        // アクセスモード R/Wアクセスで000bに固定
        // サービスコード順番 0000bから開始
        // ブロック番号 引数から指定
        for (int i = 0; i < size; i++) {
            bout.write(0x80);       // ブロックエレメント上位バイト 「Felicaユーザマニュアル抜粋」の4.3項参照
            bout.write(i);          // ブロック番号
        }

        byte[] msg = bout.toByteArray();
        msg[0] = (byte) msg.length; // 先頭１バイトはデータ長
        return msg;
    }

    /**
     * Read Without Encryption応答の解析。
     * @param res byte[]
     * @return 文字列表現
     * @throws Exception
     */
    private byte[][] parse(byte[] res) throws Exception {
        // res[10] エラーコード。0x00の場合が正常
        if (res[10] != 0x00)
            throw new RuntimeException("Read Without Encryption Command Error. error code : " + String.valueOf(res[10]));

        // res[12] 応答ブロック数
        // res[13 + n * 16] 実データ 16(byte/ブロック)の繰り返し
        int size = res[12];
        byte[][] data = new byte[size][16];
        String str = "";
        for (int i = 0; i < size; i++) {
            byte[] tmp = new byte[16];
            int offset = 13 + i * 16;
            for (int j = 0; j < 16; j++) {
                tmp[j] = res[offset + j];
            }

            data[i] = tmp;
        }
        return data;
    }
}