package com.example.batch.sample.job08_s3_io;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/**
 * サンプル08【骨格】: ローカルファイルシステムから入力を取得するプロバイダ。
 *
 * <p>プロファイル {@code local}（既定）で使用される。</p>
 */
public class LocalFileResourceProvider implements InputResourceProvider {

    @Override
    public Resource resolve(String location) {
        return new FileSystemResource(location);
    }
}
