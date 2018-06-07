export class Certificate {
  id?: string;
  name: string;
  format: string;
  data: string;
  active: true;

  static from({id, name, data}: {id?: string, name: string, data: string}): Certificate {
    const certificate = new Certificate();
    if(id) {
      certificate.id = id;
    }
    certificate.name = name;
    certificate.format = 'PEM';
    certificate.data = data;
    certificate.active = true;

    return certificate;
  }
}
