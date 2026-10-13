import { useCallback, useState } from 'react';
import PublicLayout from '../../../layouts/PublicLayout';
import { BookingDialog, RoomDialog } from '../components/Dialogs';
import Hero from '../components/Hero';
import LocationsSection from '../components/LocationsSection';
import PricingSection from '../components/PricingSection';
import SpacesSection from '../components/SpacesSection';
import { ExperienceSection, FaqSection, FinalCta, IntroSection } from '../components/StorySections';
import { useLandingEffects } from '../hooks/useLandingEffects';

// Kết hợp các khu vực giới thiệu và hộp thoại chọn chi nhánh/phòng.
export default function HomePage() {
  const [selectedRoom, setSelectedRoom] = useState(null);
  const [bookingOpen, setBookingOpen] = useState(false);
  const [branchIndex, setBranchIndex] = useState(0);
  useLandingEffects();
  // Mở hộp thoại đặt phòng với chi nhánh đã chọn nếu có.
  const openBooking = useCallback((index) => { if (Number.isInteger(index)) setBranchIndex(index); setBookingOpen(true); }, []);
  // Đóng hộp thoại đặt phòng.
  const closeBooking = useCallback(() => setBookingOpen(false), []);
  // Đóng hộp thoại chi tiết phòng.
  const closeRoom = useCallback(() => setSelectedRoom(null), []);
  return <PublicLayout onBook={openBooking}><main id="main"><Hero /><IntroSection /><SpacesSection onSelectRoom={setSelectedRoom} /><ExperienceSection onBook={openBooking} /><PricingSection onBook={openBooking} /><LocationsSection onBook={openBooking} /><FaqSection /><FinalCta onBook={openBooking} /></main><RoomDialog room={selectedRoom} onClose={closeRoom} onBook={openBooking} /><BookingDialog isOpen={bookingOpen} branchIndex={branchIndex} onBranchChange={setBranchIndex} onClose={closeBooking} /></PublicLayout>;
}

