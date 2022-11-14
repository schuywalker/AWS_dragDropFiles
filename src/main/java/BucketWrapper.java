import java.util.List;

import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.S3ObjectSummary;

public class BucketWrapper {
	
	protected Bucket bucket = null;
	protected List<S3ObjectSummary> objectList = null;
	
	public BucketWrapper(Bucket bucket) {
		this.bucket = bucket;
	}
	
	public void setObjectList(List<S3ObjectSummary> objectsSummary) {
		this.objectList = objectsSummary;
	}
	
}