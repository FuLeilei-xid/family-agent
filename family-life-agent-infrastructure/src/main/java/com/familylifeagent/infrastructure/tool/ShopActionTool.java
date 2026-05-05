package com.familylifeagent.infrastructure.tool;

import com.familylifeagent.infrastructure.entity.ReservationEntity;
import com.familylifeagent.infrastructure.entity.ShopEntity;
import com.familylifeagent.infrastructure.entity.VoucherEntity;
import com.familylifeagent.infrastructure.repository.ReservationRepository;
import com.familylifeagent.infrastructure.repository.ShopRepository;
import com.familylifeagent.infrastructure.repository.VoucherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 服务执行工具 — 预约、领券等写操作。
 * 直接写入数据库，支持真实的服务闭环演示。
 */
@Component
public class ShopActionTool {

    private static final Logger log = LoggerFactory.getLogger(ShopActionTool.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ShopRepository shopRepository;
    private final VoucherRepository voucherRepository;
    private final ReservationRepository reservationRepository;

    public ShopActionTool(ShopRepository shopRepository,
                          VoucherRepository voucherRepository,
                          ReservationRepository reservationRepository) {
        this.shopRepository = shopRepository;
        this.voucherRepository = voucherRepository;
        this.reservationRepository = reservationRepository;
    }

    @Tool(description = "为用户预约/排号一家店铺。必须先和用户确认时间（格式yyyy-MM-dd HH:mm）、人数和特殊需求（如无烟区、靠窗、宝宝椅），确认后再调用。返回预约编号和状态。")
    public ReserveResult reserveShop(Long shopId,
                                      String reserveTime,
                                      Long userId,
                                      Integer partySize,
                                      String specialRequests) {
        if (shopId == null || reserveTime == null || userId == null) {
            return new ReserveResult(null, null, "FAILED", "缺少必要参数：shopId、reserveTime、userId 不能为空");
        }

        ShopEntity shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new IllegalArgumentException("店铺不存在: shopId=" + shopId));

        LocalDateTime time;
        try {
            time = LocalDateTime.parse(reserveTime, TIME_FMT);
        } catch (DateTimeParseException e) {
            return new ReserveResult(shopId, shop.getShopName(), "FAILED",
                    "时间格式错误，请使用 yyyy-MM-dd HH:mm 格式，例如 2026-05-01 18:30");
        }

        if (time.isBefore(LocalDateTime.now())) {
            return new ReserveResult(shopId, shop.getShopName(), "FAILED", "预约时间不能早于当前时间");
        }

        ReservationEntity reservation = new ReservationEntity();
        reservation.setShop(shop);
        reservation.setUserId(userId);
        reservation.setReserveTime(time);
        reservation.setPartySize(partySize != null ? partySize : 2);
        reservation.setSpecialRequests(specialRequests);
        reservation.setStatus("CONFIRMED");
        reservation.setCreatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);

        String summary = String.format("%s %d人 %s %s",
                shop.getShopName(), reservation.getPartySize(),
                time.format(TIME_FMT),
                specialRequests != null ? specialRequests : "");
        log.info("预约成功: reservationId={}, {}", reservation.getId(), summary);

        return new ReserveResult(shopId, shop.getShopName(), "CONFIRMED",
                "预约成功！编号 #" + reservation.getId() + "，" + summary);
    }

    @Tool(description = "帮用户领取指定优惠券。必须先从 voucherList 查询可用券后，让用户确认要领哪张，再调用本工具。返回领取结果。")
    public ClaimVoucherResult claimVoucher(Long voucherId, Long userId) {
        if (voucherId == null || userId == null) {
            return new ClaimVoucherResult(null, null, "FAILED", "voucherId 和 userId 不能为空");
        }

        VoucherEntity voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new IllegalArgumentException("优惠券不存在: voucherId=" + voucherId));

        if ("CLAIMED".equals(voucher.getStatus())) {
            return new ClaimVoucherResult(voucherId, voucher.getTitle(), "ALREADY_CLAIMED",
                    "该券已被领取过，看看其他优惠券吧");
        }
        if ("EXPIRED".equals(voucher.getStatus())) {
            return new ClaimVoucherResult(voucherId, voucher.getTitle(), "FAILED", "该券已过期");
        }

        voucher.setStatus("CLAIMED");
        voucherRepository.save(voucher);
        log.info("优惠券领取成功: voucherId={}, title={}, userId={}", voucherId, voucher.getTitle(), userId);

        return new ClaimVoucherResult(voucherId, voucher.getTitle(), "CLAIMED",
                "领取成功！" + voucher.getTitle() + " 已放入您的账户，到店出示即可使用");
    }

    public record ReserveResult(Long shopId, String shopName, String status, String message) {}

    public record ClaimVoucherResult(Long voucherId, String title, String status, String message) {}
}
