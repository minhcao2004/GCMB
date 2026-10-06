export const formatPhone = (phone) => phone.replace(/(\d{4})(\d{3})(\d{3})/, '$1 $2 $3');
export const branchTypeName = (type) => type === 'both' ? 'CINEMA & MUSIC' : type === 'music' ? 'MUSIC / GENZ BOX' : 'CINEMA';

