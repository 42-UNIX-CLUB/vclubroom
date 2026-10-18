package pt.vclubroom.media.infrastructure;

import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;
import pt.vclubroom.media.application.ObjectStorage;
import pt.vclubroom.media.application.PresignedUpload;
import pt.vclubroom.media.application.StoredObject;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** Adapter: ObjectStorage over the S3 API (SeaweedFS in this project). */
@Component
class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    S3ObjectStorage(S3Client s3, S3Presigner presigner, StorageProperties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override
    public PresignedUpload presignUpload(String bucket, String key, String contentType, Duration ttl) {
        PutObjectRequest put = PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(
            PutObjectPresignRequest.builder().signatureDuration(ttl).putObjectRequest(put).build());
        return new PresignedUpload(presigned.url().toString(), presigned.signedHeaders(), presigned.expiration());
    }

    @Override
    public String presignDownload(String bucket, String key, Duration ttl) {
        GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(key).build();
        return presigner.presignGetObject(
                GetObjectPresignRequest.builder().signatureDuration(ttl).getObjectRequest(get).build())
            .url().toString();
    }

    @Override
    public String publicUrl(String bucket, String key) {
        return properties.publicEndpoint().replaceAll("/+$", "") + "/" + bucket + "/" + key;
    }

    @Override
    public Optional<StoredObject> head(String bucket, String key) {
        try {
            HeadObjectResponse r = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return Optional.of(new StoredObject(r.contentLength(), r.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public void delete(String bucket, String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public void ensureBucket(String bucket) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e;
            }
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }
}
