import UIKit

public extension UIImage {
    func resized(toMaxDimension maxDimension: CGFloat = 1280) -> UIImage {
        let size = self.size
        let maxOriginal = max(size.width, size.height)
        
        if maxOriginal <= maxDimension {
            return self
        }
        
        let ratio = maxDimension / maxOriginal
        let newSize = CGSize(width: size.width * ratio, height: size.height * ratio)
        
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1.0 // Use 1.0 so we don't multiply by screen scale
        
        let renderer = UIGraphicsImageRenderer(size: newSize, format: format)
        return renderer.image { _ in
            self.draw(in: CGRect(origin: .zero, size: newSize))
        }
    }
}
