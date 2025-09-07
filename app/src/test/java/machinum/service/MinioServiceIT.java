package machinum.service;

import machinum.TestApplication;
import machinum.service.AudioService.FileMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.wiremock.spring.EnableWireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;


/**
 * Not all moсks works as required.
 * This integration test class uses WireMock to mock MinIO responses and tests the MinioService.
 * The tests are disabled by default, as they require a running MinIO server or WireMock setup.
 * To run these tests, enable them and ensure that WireMock is properly configured.
 */
@Disabled
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.minio.endpoint=${wiremock.server.baseUrl}",
                "app.minio.bucketName=test-bucket",
                "app.minio.check-enabled=false",
                "app.minio.enabled=true",
        }
)
@EnableWireMock
class MockMinioServiceIT {

    @Autowired
    private MinioService minioService;

    @Value("${app.minio.bucketName}")
    private String bucketName;

    @Value("${wiremock.server.baseUrl}")
    private String wireMockUrl;


    @BeforeEach
    public void setup() {
        // Mock list buckets response
        stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("<ListAllMyBucketsResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"><Owner><ID>owner-id</ID><DisplayName>display-name</DisplayName></Owner><Buckets><Bucket><Name>test-bucket</Name><CreationDate>2023-04-01T12:00:00.000Z</CreationDate></Bucket></Buckets></ListAllMyBucketsResult>")
                        .withHeader("Content-Type", "application/xml")));

        // Mock put object response
        stubFor(put(urlEqualTo("/test-bucket/test-object"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Mock get pre-signed URL response
        stubFor(get(urlEqualTo("/test-bucket/test-object?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=test%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20230401T120000Z&X-Amz-Expires=3600&X-Amz-Signature=signature&X-Amz-SignedHeaders=host"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("test-body")));

        // Mock download content response
        stubFor(get(urlEqualTo("/download?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=test%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20230401T120000Z&X-Amz-Expires=3600&X-Amz-Signature=signature&X-Amz-SignedHeaders=host"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("test-body")));
    }

    @Test
    @Disabled
    public void testUploadToMinio() throws Exception {
        byte[] mp3Data = "mp3-data".getBytes();
        String objectKey = "test-object";
        String fileId = "file-id";
        String chapterId = "chapter-id";
        FileMetadata output = new FileMetadata();

        minioService.uploadToMinio(mp3Data, objectKey, fileId, chapterId, output);
    }

    @Test
    @Disabled
    public void testGetPreSignedUrl() throws Exception {
        String objectKey = "test-object";
        String preSignedUrl = minioService.getPreSignedUrl(objectKey);

        assertNotNull(preSignedUrl);
        assertTrue(preSignedUrl.contains("X-Amz-Algorithm"));
    }

    @Test
    @Disabled
    public void testDownloadContent() throws Exception {
        String preSignedUrl = "/download?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=test%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20230401T120000Z&X-Amz-Expires=3600&X-Amz-Signature=signature&X-Amz-SignedHeaders=host";
        byte[] content = minioService.downloadContent(preSignedUrl);

        assertNotNull(content);
        assertEquals("test-body", new String(content));
    }

}