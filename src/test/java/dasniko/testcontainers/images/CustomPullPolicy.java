package dasniko.testcontainers.images;

import org.testcontainers.images.AbstractImagePullPolicy;
import org.testcontainers.images.ImageData;
import org.testcontainers.utility.DockerImageName;

public class CustomPullPolicy extends AbstractImagePullPolicy {
    @Override
    protected boolean shouldPullCached(DockerImageName imageName, ImageData localImageData) {
        return !imageName.getRegistry().equalsIgnoreCase("localhost");
    }
}
