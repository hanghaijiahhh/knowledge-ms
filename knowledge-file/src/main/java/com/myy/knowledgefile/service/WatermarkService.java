package com.myy.knowledgefile.service;

import com.myy.common.exception.BizException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 水印服务：在图片/PDF 上叠加用户信息水印
 * <p>
 * 水印内容 = 用户名 + 时间戳，倾斜平铺，半透明
 */
@Service
public class WatermarkService {

    private static final Color WATERMARK_COLOR = new Color(128, 128, 128, 60);
    private static final Font WATERMARK_FONT = new Font("Microsoft YaHei", Font.PLAIN, 20);

    /**
     * 对文件流添加水印，根据 MIME 类型分别处理
     */
    public byte[] applyWatermark(byte[] fileBytes, String contentType,
                                  String watermarkText) throws Exception {
        if (contentType == null) return fileBytes;

        if (contentType.startsWith("image/")) {
            return addImageWatermark(fileBytes, watermarkText);
        }
        if ("application/pdf".equals(contentType)) {
            return addPdfWatermark(fileBytes, watermarkText);
        }
        // 其他类型（Word、Excel等）暂不加水印，直接返回原文件
        return fileBytes;
    }

    /** 生成水印文字：用户名 @ 时间 */
    public static String buildWatermarkText(String username) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        return username + " @ " + time;
    }

    // ==================== 图片水印 ====================

    private byte[] addImageWatermark(byte[] imageBytes, String text) {
        try (InputStream is = new ByteArrayInputStream(imageBytes)) {
            BufferedImage image = ImageIO.read(is);
            if (image == null) throw new BizException("无法解析图片");

            Graphics2D g2d = (Graphics2D) image.getGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(WATERMARK_COLOR);
            g2d.setFont(WATERMARK_FONT);

            // 倾斜平铺水印
            int spacing = 200;
            int imgW = image.getWidth();
            int imgH = image.getHeight();
            for (int y = -imgH; y < imgH * 2; y += spacing) {
                for (int x = -imgW; x < imgW * 2; x += spacing + 200) {
                    g2d.rotate(Math.toRadians(-30), x, y);
                    g2d.drawString(text, x, y);
                    g2d.rotate(Math.toRadians(30), x, y);
                }
            }
            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new BizException("图片水印处理失败: " + e.getMessage());
        }
    }

    // ==================== PDF 水印 ====================

    private byte[] addPdfWatermark(byte[] pdfBytes, String text) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {

            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            for (PDPage page : doc.getPages()) {
                float pageW = page.getMediaBox().getWidth();
                float pageH = page.getMediaBox().getHeight();

                PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true);

                // 半透明效果
                PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                gs.setNonStrokingAlphaConstant(0.1f);
                cs.setGraphicsStateParameters(gs);
                cs.setNonStrokingColor(0.5f, 0.5f, 0.5f);
                cs.setFont(font, 18);

                // 平铺
                double angle = Math.toRadians(-30);
                float spacing = 150;
                for (float y = -pageH; y < pageH * 2; y += spacing) {
                    for (float x = -pageW; x < pageW * 2; x += spacing + 200) {
                        cs.beginText();
                        cs.setTextMatrix(new Matrix(
                                (float) Math.cos(angle), (float) Math.sin(angle),
                                (float) -Math.sin(angle), (float) Math.cos(angle),
                                x, y));
                        cs.showText(text);
                        cs.endText();
                    }
                }
                cs.close();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new BizException("PDF水印处理失败: " + e.getMessage());
        }
    }
}
